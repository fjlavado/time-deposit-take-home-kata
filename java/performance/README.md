# Performance testing

See [the measured local results](RESULTS.md) for the 100,000-deposit evaluation,
including the mixed-load saturation, late admission after timeout, and recovery.

The performance environment uses PostgreSQL 17.6 in its own Compose project,
database (`time_deposits_perf`), named volume, and localhost port (default 5433).
Run these commands from `java/`. Prerequisites: Docker Compose, Maven, JDK 25+,
Python 3. Keep the application stopped when resetting the dataset. Docker must be
local for the published localhost port; on this Linux host use
`export DOCKER_HOST=unix:///var/run/docker.sock`. If 5433 is occupied, set
`export PERF_DB_PORT=55433` and use that port in `DB_URL`.

## Database and repeatable sample

```sh
docker compose -f performance/compose.yaml up -d --wait
mvn package
DB_URL=jdbc:postgresql://localhost:5433/time_deposits_perf \
  java -Xms256m -Xmx512m -jar bootstrap/target/bootstrap-1.0-SNAPSHOT.jar \
  --spring.main.web-application-type=none
```

This non-web startup applies Flyway and exits. Then seed:

```sh
docker compose -f performance/compose.yaml exec -T postgres \
  psql -X -U time_deposit -d time_deposits_perf \
  -v deposits=100000 -v max_withdrawals=10 < performance/seed.sql
```

Every seed resets deposits, withdrawals, identities, and execution history in one
transaction, then runs `ANALYZE`. It never modifies Flyway history. The default
contains 100,000 deposits and approximately 500,000 withdrawals. Set `deposits`
to 1000000 for the larger sample, or change `max_withdrawals` (including zero).
Plan/age cycles cover all three plans at days 0, 30, 31, 45, 46, 365, 366, and
730. Amounts, dates, and 0..10 withdrawals per deposit are deterministic.

### Verify the seed fixture

On the migrated performance database, run twice to verify repeatable resets:

```sh
docker compose -f performance/compose.yaml exec -T postgres \
  psql -X -U time_deposit -d time_deposits_perf \
  -v deposits=24 -v max_withdrawals=10 < performance/seed.sql
docker compose -f performance/compose.yaml exec -T postgres \
  psql -X -U time_deposit -d time_deposits_perf < performance/verify-seed.sql
```

Reseed the full dataset before benchmarking. Stop PostgreSQL with
`docker compose -f performance/compose.yaml down`; its volume retains data.
`PERF_DB_PORT` overrides the published database port.

## Automated evaluation (recommended)

```sh
mvn verify
python3 -m unittest discover -s performance -p 'test_*.py'
python3 performance/run.py
```

The runner starts the isolated database, applies Flyway through a non-web Boot
startup, verifies the 24-deposit fixture twice, resets/seeds the requested dataset,
and launches the application on port 8082. It requires Linux `ps` for resource
samples. Use a local Docker daemon and an unused application port. It shuts down
its application gracefully afterwards; PostgreSQL and its volume remain available.
Run only one evaluation at a time against this performance database.

For this development host (5433 already occupied):

```sh
DOCKER_HOST=unix:///var/run/docker.sock PERF_DB_PORT=55433 python3 performance/run.py
```

Default phases, with one request per virtual user and an **open arrival model**:

| Phase | Offered load | Duration |
| --- | --- | --- |
| Smoke | One GET and one POST, followed by SQL completion wait | Until completion |
| Baseline | 50 GET/s | 60 seconds |
| Mixed stress | GET at 25, 100, 175/s; 20 simultaneous POST attempts plus 0.1 POST/s | Three 30-second levels, two 10-second ramps |
| Completion wait | SQL audit and database activity inspection, no POST polling | Up to 180 seconds |
| Recovery | 10 GET/s, after background processing ends | 30 seconds |

GET chooses pages uniformly across the entire dataset, including deep offsets
and a potentially partial final page. Page metadata, content count, and nested
withdrawals presence are checked. Caching is disabled. The one-request users
include connection setup cost. Each HTTP request times out after 10 seconds.
The first smoke run also warms application and database paths; no cold-start
latency claim is made from later phases. Expected total is about 5–10 minutes
including job waits and JVM/report overhead, excluding the build; slow machines
may exceed this. The completion timeout is per wait, not a total-run deadline.

The completion wait requires five seconds with terminal audits and no other
busy database client sessions before starting recovery. Final integrity/audit
snapshots are taken after graceful application shutdown. A timed-out HTTP POST
can still be admitted later: `unacknowledgedAuditIds` exposes these UUIDs and
UUID reconciliation fails rather than hiding the uncertain client outcome.

POST checks `202 STARTED` / `409 RUNNING` and UUID bodies. Busy responses are
**successful admission behavior**, counted separately from unexpected HTTP/check
failures. Each accepted UUID must correspond to a completed audit row; all busy
UUIDs must refer to accepted jobs. Later POST attempts can start another job after
completion, crediting interest again. The runner never uses POST to poll completion.

### Configuration

```sh
# Fast harness verification, including real HTTP and background processing:
python3 performance/run.py --quick --deposits 24
# Only verify SQL reset/fixture behavior:
python3 performance/run.py --fixture-only
# Heavier dataset / wider pages / longer job completion budget:
python3 performance/run.py --deposits 1000000 --page-size 100 --completion-timeout 900
# Increase load until latency/error budgets break:
python3 performance/run.py --start-rate 100 --step-rate 150 --levels 4
```

`python3 performance/run.py --help` lists all options. `--quick` shortens load
phases to 5 seconds (two mixed levels, 2-second ramp), without changing dataset
size. Defaults are 100,000 deposits, max 10 withdrawals, page size 20,
`--p95-ms 1000`, and `--max-error-percent 1`. These are initial evaluation
budgets, not established product SLOs. Exceeding a budget produces a nonzero exit
code; the full runner still collects later phases and integrity checks. A smoke
failure stops the evaluation. Incomplete jobs produce a failure artifact and are
not reported as completed; graceful application shutdown can wait further for
the current job. If shutdown also exceeds the timeout, the runner prints the
still-draining PID and does not forcibly kill it.

### Gatling directly

The standalone Maven module pins Gatling 3.16.0 and plugin 4.21.12, isolating
Gatling's Netty/Jackson dependencies from Boot dependency management. The parent
reactor includes it only with `-Pperformance`; neither ordinary verification nor
that profile starts HTTP load without an explicit `gatling:test` goal.

Start the seeded application separately:

```sh
DB_URL=jdbc:postgresql://localhost:5433/time_deposits_perf PORT=8082 \
  java -Xms256m -Xmx512m -jar bootstrap/target/bootstrap-1.0-SNAPSHOT.jar
```

Then in another terminal:

```sh
mvn -Pperformance -pl performance test-compile
mvn -B -f performance/pom.xml gatling:test \
  -Dperf.mode=read -Dperf.baseUrl=http://localhost:8082 \
  -Dperf.deposits=100000 -Dperf.pageSize=20 -Dperf.rate=50 -Dperf.seconds=60
mvn -B -f performance/pom.xml gatling:test \
  -Dperf.mode=mixed -Dperf.deposits=100000 \
  -Dperf.rate=25 -Dperf.step=75 -Dperf.levels=3 \
  -Dperf.levelSeconds=30 -Dperf.rampSeconds=10 \
  -Dperf.contention=20 -Dperf.postRate=0.1
```

Other properties: `perf.p95Millis`, `perf.maxErrorPercent`, `perf.outcomes` (JSON
counter output path); `perf.mode=smoke` checks both operations. Supply the actual
seed count: GET checks fail on an empty or incorrectly sized database. Gatling
alone checks HTTP acceptance, not asynchronous job completion; use the runner
for end-to-end evaluation. Direct HTML reports are under
`performance/target/gatling/<run>/index.html`.

## Artifacts and interpreting results

Each automated run writes `performance/target/evaluation-<UTC timestamp>/`:

- `summary.json`: per-phase HTTP counts, failures, throughput, p50/p95/p99/max,
  separate accepted/busy counters, UUID reconciliation, completed job durations,
  deposits/second, and integrity/assertion outcomes.
- `<phase>/gatling/index.html`: complete Gatling graphs, including stepped-load
  timelines. `command.json`, `outcomes.json`, and `maven.log` accompany each phase.
  The runner archives the new report from the plugin's fixed results directory.
- `environment.json`: JVM/OS/CPU/RAM, PostgreSQL settings, and exact parameters.
  Application and Gatling heaps are 256 MiB initial / 512 MiB maximum; Hikari uses
  Boot's default maximum of 10 connections. PostgreSQL uses stock image settings,
  with durability enabled and no CPU/memory container limits.
- `resources.jsonl`: samples every 10 seconds of application lifetime-average CPU
  percentage, RSS in KiB, thread count, host load average, database activity/waits,
  cumulative transaction/I/O/row/deadlock counters, and estimated live/dead rows.
  Database counters are cumulative across runs; compare first/last sample deltas.
- `application.log`, `gc.log`, `before.json`, `jobs.json`, `partial-summary.json`;
  `failure.json` on an orchestration error. Budget failures are recorded in
  `summary.json` and the phase's Maven log.

Report latency/throughput comes from Gatling's **all-request** statistics (including
failures); throughput averages over the simulation duration. Admission latency
is not processing latency: audit duration runs from reservation to terminal audit,
including queueing and commit/audit overhead. Fingerprints cover every immutable
deposit/withdrawal field using count plus an order-independent PostgreSQL hash
sum (a diagnostic check, not a collision-free proof). Versions must be identical
for all deposits and equal the completed-job count after a fresh seed.

Evaluate baseline versus mixed p95/p99, achieved versus offered traffic, and the
HTML timeline at each plateau. Inspect GC, RSS, pool activity, database waits,
and child-row churn when latency rises. A passing short run does not establish a
maximum capacity or prove absence of a memory leak. For capacity discovery,
increase rate/plateau duration; for soak testing, extend `--level-seconds`.
Application, database, and generator share the local machine; resource contention
and cache effects limit comparisons with independently hosted production systems.

Generated artifacts are ignored through the repository's existing `target/` rule.
