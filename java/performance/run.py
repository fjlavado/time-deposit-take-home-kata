#!/usr/bin/env python3
"""Repeatable local Gatling evaluation; Python standard library only."""
import argparse
from datetime import datetime, timezone
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import platform
import subprocess
import threading
import time
import urllib.error
import urllib.request

HERE = Path(__file__).resolve().parent
JAVA = HERE.parent
COMPOSE = ["docker", "compose", "-f", str(HERE / "compose.yaml")]
PSQL = COMPOSE + ["exec", "-T", "postgres", "psql", "-X", "-U", "time_deposit",
                  "-d", "time_deposits_perf", "-v", "ON_ERROR_STOP=1"]


def command(args, **kwargs):
    return subprocess.run(args, cwd=JAVA, check=True, text=True, **kwargs)


def db(sql):
    return json.loads(command(PSQL + ["-Atq", "-c", sql], capture_output=True).stdout)


def seed(deposits, max_withdrawals):
    command(PSQL + ["-v", f"deposits={deposits}", "-v", f"max_withdrawals={max_withdrawals}"],
            input=(HERE / "seed.sql").read_text())


def snapshot():
    # Order-independent fingerprints include every immutable field, not only totals.
    return db("""SELECT json_build_object(
        'deposits', (SELECT count(*) FROM "timeDeposits"),
        'withdrawals', (SELECT count(*) FROM withdrawals),
        'depositMetadata', (SELECT coalesce(sum(hashtextextended(
            ROW(id, "planType", days)::text, 0)::numeric), 0)::text FROM "timeDeposits"),
        'withdrawalData', (SELECT coalesce(sum(hashtextextended(
            ROW(id, "timeDepositId", amount, date)::text, 0)::numeric), 0)::text FROM withdrawals),
        'minVersion', (SELECT min(version) FROM "timeDeposits"),
        'maxVersion', (SELECT max(version) FROM "timeDeposits"),
        'balanceSum', (SELECT sum(balance)::text FROM "timeDeposits"))""")


def jobs():
    return db("""SELECT coalesce(json_agg(j ORDER BY started_at), '[]'::json) FROM (
        SELECT id, status, started_at, finished_at,
            extract(epoch FROM (finished_at - started_at))::double precision AS seconds
        FROM balance_update_executions) j""")


class StatisticsParser(HTMLParser):
    """Extract the pinned Gatling 3.16 report table (simulation.log is binary)."""
    def __init__(self):
        super().__init__()
        self.rows = {}
        self.row = None
        self.column = None

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "tr" and attrs.get("id", "").startswith("req_"):
            self.row = {}
        if self.row is not None:
            if tag == "td":
                self.column = next((c for c in attrs.get("class", "").split() if c.startswith("col-")), None)
            if tag == "span" and attrs.get("class") == "ellipsed-name":
                self.column = "name"

    def handle_data(self, data):
        if self.row is not None and self.column and data.strip():
            self.row[self.column] = self.row.get(self.column, "") + data.strip()

    def handle_endtag(self, tag):
        if tag in ("span", "td"):
            self.column = None
        if tag == "tr" and self.row is not None:
            row = self.row
            self.rows[row["name"]] = {
                "requests": int(row["col-2"].replace(",", "")),
                "failures": int(row["col-4"].replace(",", "")),
                "observedRps": float(row["col-6"].replace(",", "")),
                **{key: float(row[col].replace(",", "")) for key, col in
                   (("p50Millis", "col-8"), ("p95Millis", "col-10"),
                    ("p99Millis", "col-11"), ("maxMillis", "col-12"))},
            }
            self.row = None


def summarize_report(path):
    parser = StatisticsParser()
    parser.feed(path.read_text())
    if not parser.rows:
        raise ValueError(f"No request statistics in {path}")
    return parser.rows


def wait_for_jobs(timeout):
    deadline = time.monotonic() + timeout
    while True:
        executions = jobs()
        if not any(job["status"] in ("STARTED", "RUNNING") for job in executions):
            return executions
        if time.monotonic() >= deadline:
            raise TimeoutError(f"Background job still active after {timeout}s; inspect jobs.json and application.log")
        time.sleep(2)


def sample_resources(stop, app, path):
    with path.open("w") as output:
        while not stop.is_set():
            try:
                stats = db("""SELECT json_build_object(
                    'database', (SELECT row_to_json(s) FROM (SELECT numbackends, xact_commit,
                        xact_rollback, blks_read, blks_hit, tup_inserted, tup_updated, tup_deleted,
                        deadlocks, temp_bytes FROM pg_stat_database WHERE datname = current_database()) s),
                    'tables', (SELECT json_agg(s) FROM (SELECT relname, n_live_tup, n_dead_tup
                        FROM pg_stat_user_tables WHERE relname IN ('timeDeposits', 'withdrawals')) s),
                    'activity', (SELECT json_agg(s) FROM (SELECT state, wait_event_type, wait_event,
                        count(*) FROM pg_stat_activity WHERE datname = current_database()
                        GROUP BY state, wait_event_type, wait_event) s))""")
                stats.update(utc=datetime.now(timezone.utc).isoformat(),
                             appPid=app.pid, appExitCode=app.poll(),
                             appCpuRssThreads=command(["ps", "-p", str(app.pid), "-o", "pcpu=,rss=,nlwp="],
                                                      capture_output=True).stdout.strip(),
                             loadAverage=os.getloadavg())
            except Exception as error:
                stats = {"samplingError": str(error)}
            output.write(json.dumps(stats) + "\n")
            output.flush()
            stop.wait(10)


def run_phase(name, mode, args, output, properties):
    phase = output / name
    phase.mkdir()
    props = {"mode": mode, "baseUrl": f"http://localhost:{args.app_port}",
             "deposits": args.deposits, "pageSize": args.page_size,
             "p95Millis": args.p95_ms, "maxErrorPercent": args.max_error_percent,
             "outcomes": phase / "outcomes.json", **properties}
    # Plugin 4.21 makes resultsFolder read-only. Archive the newly produced run after it finishes.
    results = HERE / "target/gatling"
    existing = set(results.glob("*/simulation.log"))
    invocation = ["mvn", "-B", "-q", "-f", str(HERE / "pom.xml"), "gatling:test"]
    invocation += [f"-Dperf.{key}={value}" for key, value in props.items()]
    print(f"Running {name}: {properties}", flush=True)
    (phase / "command.json").write_text(json.dumps(invocation, indent=2) + "\n")
    with (phase / "maven.log").open("w") as log:
        completed = subprocess.run(invocation, cwd=JAVA, stdout=log, stderr=subprocess.STDOUT)
    logs = list(set(results.glob("*/simulation.log")) - existing)
    if len(logs) != 1:
        raise RuntimeError(f"Gatling did not produce one report for {name}; inspect {phase / 'maven.log'}")
    archive = phase / "gatling"
    logs[0].parent.rename(archive)
    return {"exitCode": completed.returncode, "http": summarize_report(archive / "index.html"),
            "outcomes": json.loads((phase / "outcomes.json").read_text()),
            "report": str(archive / "index.html")}


def evaluate(args, output):
    command(COMPOSE + ["up", "-d", "--wait"])
    jar = JAVA / "bootstrap/target/bootstrap-1.0-SNAPSHOT.jar"
    if not jar.exists():
        raise RuntimeError("Build the application first: mvn verify")
    env = {**os.environ, "DB_URL": f"jdbc:postgresql://localhost:{args.db_port}/time_deposits_perf",
           "DB_USERNAME": "time_deposit", "DB_PASSWORD": "time_deposit", "PORT": str(args.app_port)}
    java = ["java", "-Xms256m", "-Xmx512m"]
    with (output / "migration.log").open("w") as log:
        command(java + ["-jar", str(jar), "--spring.main.web-application-type=none"],
                env=env, stdout=log, stderr=subprocess.STDOUT)
    for _ in range(2):
        seed(24, 10)
        command(PSQL, input=(HERE / "verify-seed.sql").read_text())
    if args.fixture_only:
        return {"fixture": "passed twice"}
    seed(args.deposits, args.max_withdrawals)
    before = snapshot()
    (output / "before.json").write_text(json.dumps(before, indent=2) + "\n")
    metadata = {"utc": datetime.now(timezone.utc).isoformat(), "platform": platform.platform(),
                "cpuCount": os.cpu_count(), "arguments": vars(args),
                "java": command(["java", "-version"], capture_output=True).stderr,
                "docker": {key: value for key, value in json.loads(command(
                    ["docker", "info", "--format", "{{json .}}"], capture_output=True).stdout).items()
                    if key in ("ServerVersion", "NCPU", "MemTotal", "KernelVersion", "OperatingSystem", "Driver")},
                "postgresSettings": db("""SELECT json_object_agg(name, setting) FROM pg_settings
                    WHERE name IN ('max_connections', 'shared_buffers', 'work_mem', 'effective_cache_size',
                                   'fsync', 'synchronous_commit', 'full_page_writes')"""),
                "appJvm": java[1:], "hikariMaximumPoolSize": 10}
    (output / "environment.json").write_text(json.dumps(metadata, indent=2) + "\n")
    summary = {"before": before, "phases": {}}
    with (output / "application.log").open("w") as log:
        app = subprocess.Popen(java + [f"-Xlog:gc:file={output / 'gc.log'}", "-jar", str(jar)],
                               cwd=JAVA, env=env, stdout=log, stderr=subprocess.STDOUT)
        stop = threading.Event()
        monitor = None
        try:
            for _ in range(120):
                if app.poll() is not None:
                    raise RuntimeError("Application exited; inspect application.log")
                try:
                    with urllib.request.urlopen(f"http://localhost:{args.app_port}/time-deposits?size=1", timeout=1) as response:
                        if json.load(response)["totalElements"] == args.deposits:
                            break
                except (urllib.error.URLError, TimeoutError):
                    pass
                time.sleep(0.5)
            else:
                raise TimeoutError("Application startup exceeded 60 seconds")
            monitor = threading.Thread(target=sample_resources,
                                       args=(stop, app, output / "resources.jsonl"), daemon=True)
            monitor.start()
            phases = summary["phases"]
            phases["smoke"] = run_phase("smoke", "smoke", args, output, {})
            if phases["smoke"]["exitCode"]:
                raise RuntimeError("Smoke failed; inspect smoke/maven.log")
            wait_for_jobs(args.completion_timeout)
            phases["baseline"] = run_phase("baseline", "read", args, output,
                                           {"rate": args.baseline_rate, "seconds": args.read_seconds})
            phases["mixed"] = run_phase("mixed", "mixed", args, output,
                                        {"rate": args.start_rate, "step": args.step_rate, "levels": args.levels,
                                         "levelSeconds": args.level_seconds, "rampSeconds": args.ramp_seconds,
                                         "contention": args.contention, "postRate": args.post_rate})
            wait_for_jobs(args.completion_timeout)
            phases["recovery"] = run_phase("recovery", "read", args, output,
                                           {"rate": 10, "seconds": args.recovery_seconds})
            after = snapshot()
            executions = jobs()
            accepted = [id_ for phase in phases.values() for id_ in phase["outcomes"]["acceptedIds"]]
            busy_ids = {id_ for phase in phases.values() for id_ in phase["outcomes"]["busyIds"]}
            completed = sum(job["status"] == "COMPLETED" for job in executions)
            for job in executions:
                job["depositsPerSecond"] = round(args.deposits / job["seconds"], 2) if job["seconds"] else None
            checks = {
                "immutableDataPreserved": all(before[k] == after[k] for k in
                    ("deposits", "withdrawals", "depositMetadata", "withdrawalData")),
                "allJobsCompleted": bool(executions) and completed == len(executions),
                "acceptedIdsMatchAudit": len(accepted) == len(set(accepted))
                    and set(accepted) == {job["id"] for job in executions},
                "acceptedCountMatchesIds": all(p["outcomes"]["accepted"] == len(p["outcomes"]["acceptedIds"])
                                                for p in phases.values()),
                "busyIdsReferToAcceptedJobs": busy_ids <= set(accepted),
                "everyDepositSavedOncePerCompletedJob": after["minVersion"] == after["maxVersion"] == completed,
                "gatlingAssertionsPassed": all(p["exitCode"] == 0 for p in phases.values()),
            }
            summary.update(after=after, jobs=executions, checks=checks, passed=all(checks.values()))
            return summary
        finally:
            stop.set()
            if monitor:
                monitor.join(timeout=30)
            try:
                (output / "jobs.json").write_text(json.dumps(jobs(), indent=2) + "\n")
                (output / "partial-summary.json").write_text(json.dumps(summary, indent=2) + "\n")
            finally:
                app.terminate()  # Graceful Boot shutdown drains submitted work; never kill a balance transaction.
                try:
                    app.wait(timeout=args.completion_timeout)
                except subprocess.TimeoutExpired:
                    print(f"Application PID {app.pid} is still draining; inspect application.log. It was not killed.", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--deposits", type=int, default=100000)
    parser.add_argument("--max-withdrawals", type=int, default=10)
    parser.add_argument("--page-size", type=int, default=20)
    parser.add_argument("--app-port", type=int, default=8082)
    parser.add_argument("--db-port", type=int, default=int(os.environ.get("PERF_DB_PORT", "5433")))
    parser.add_argument("--baseline-rate", type=float, default=50)
    parser.add_argument("--read-seconds", type=int, default=60)
    parser.add_argument("--start-rate", type=float, default=25)
    parser.add_argument("--step-rate", type=float, default=75)
    parser.add_argument("--levels", type=int, default=3)
    parser.add_argument("--level-seconds", type=int, default=30)
    parser.add_argument("--ramp-seconds", type=int, default=10)
    parser.add_argument("--contention", type=int, default=20)
    parser.add_argument("--post-rate", type=float, default=0.1)
    parser.add_argument("--recovery-seconds", type=int, default=30)
    parser.add_argument("--completion-timeout", type=int, default=180)
    parser.add_argument("--p95-ms", type=int, default=1000)
    parser.add_argument("--max-error-percent", type=float, default=1)
    parser.add_argument("--fixture-only", action="store_true")
    parser.add_argument("--quick", action="store_true", help="Shorten load phases; dataset size is unchanged")
    args = parser.parse_args()
    if min(args.deposits, args.page_size, args.read_seconds, args.levels, args.level_seconds,
           args.ramp_seconds, args.contention, args.recovery_seconds, args.completion_timeout) < 1 \
            or min(args.baseline_rate, args.start_rate, args.step_rate, args.post_rate) <= 0 \
            or args.max_withdrawals < 0 or args.deposits * max(1, args.max_withdrawals) > 2147483647:
        parser.error("Counts/durations/rates must be positive and the dataset must fit integer IDs")
    if args.quick:
        args.read_seconds = args.level_seconds = args.recovery_seconds = 5
        args.ramp_seconds = 2
        args.levels = 2
    # The same port must be used by Compose and the locally launched application.
    os.environ["PERF_DB_PORT"] = str(args.db_port)
    output = HERE / "target" / ("evaluation-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
    output.mkdir(parents=True)
    print(f"Artifacts: {output}", flush=True)
    try:
        summary = evaluate(args, output)
    except Exception as error:
        (output / "failure.json").write_text(json.dumps({"error": str(error)}, indent=2) + "\n")
        raise
    (output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))
    return 0 if summary.get("passed", args.fixture_only) else 1


if __name__ == "__main__":
    raise SystemExit(main())
