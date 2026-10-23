# Local stress evaluation — 2026-10-23

## Outcome

**The read baseline passed; mixed stress exceeded the latency/error budgets.**
After queued requests and background jobs drained, low-rate reads recovered.
The evaluation returned **exit code 1**, preserving the failure rather than
relaxing the load or thresholds. All three database executions ultimately
completed, with no overlapping execution intervals and no observed loss of
deposit metadata or withdrawal data.

## Reproduce

From `java/`, using JDK 27, Maven, Python 3, and local Docker:

```sh
mvn test
DOCKER_HOST=unix:///var/run/docker.sock mvn verify
DOCKER_HOST=unix:///var/run/docker.sock mvn -Pperformance verify
python3 -m unittest discover -s performance -p 'test_*.py'
DOCKER_HOST=unix:///var/run/docker.sock PERF_DB_PORT=55433 python3 performance/run.py
```

Application build and fixture/reporting checks passed. The two Maven verification
commands do not launch stress traffic. Five Python regression checks and the
corrected quick real-HTTP evaluation (`--quick --deposits 24`) passed. A deliberate
`--p95-ms 0` run also verified assertion-failure propagation.

Authoritative full-run artifacts are under:

`performance/target/evaluation-20261023T161615111391Z/`

Open `baseline/gatling/index.html`, `mixed/gatling/index.html`, and
`recovery/gatling/index.html` there for graphs; `summary.json` contains exact
counts, UUIDs, timestamps, integrity checks, and exit codes. Generated artifacts
are local build output; this document preserves the measured findings in Git.

The application ran approximately **16:16:28–16:25:20 UTC** (about nine minutes),
with setup beginning at 16:16:15 UTC. No Maven verification suite ran concurrently
with the full load evaluation.

## Environment and workload

- AMD Ryzen 9 5950X: 16 physical cores / 32 logical CPUs; approximately 60.7 GiB
  RAM visible to Docker; Ubuntu 26.04.1, Linux 7.0.0-38, local Docker 29.4.1.
- Amazon Corretto JDK 27; Java compilation target 25; Boot 4.1.1.
- Gatling Community 3.16.0 / Maven plugin 4.21.12, Java DSL.
- Separate application and generator JVMs, each `-Xms256m -Xmx512m`.
- Hikari maximum pool size: 10; application port 8082.
- PostgreSQL 17.6 Alpine, port 55433, Docker named volume, stock image settings:
  128 MiB shared buffers, 4 MiB work memory, max 100 connections; fsync,
  synchronous commit, and full-page writes enabled. No container CPU/RAM limits.
- **100,000 deposits and 500,005 withdrawals**, deterministic seed; three plans,
  eight deposit ages, 0–10 withdrawals per deposit. Page size 20, 5,000 pages.
- Open-model, one-request virtual users; GET pages selected uniformly across
  shallow/deep offsets, HTTP caching disabled, 10-second request timeout.
- Baseline: 50 GET/s for 60 seconds. Mixed: 25, 100, 175 GET/s, each plateau
  lasting 30 seconds, with two 10-second ramps; twenty simultaneous POST attempts
  plus 0.1 POST/s over the 110-second injection. Recovery: 10 GET/s for 30 seconds.
- Budgets: GET p95 ≤ 1,000 ms and global unexpected failures ≤ 1% per phase.
  A valid `409 RUNNING` is successful admission behavior, not an unexpected failure.

## HTTP results

Latency columns include failed requests. Throughput is Gatling's all-request
event rate averaged over the simulation, including its response-draining tail.

| Phase / operation | Requests | Unexpected failures | Event rate/s | p50 ms | p95 ms | p99 ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Smoke GET | 1 | 0 | 1.00 | 156 | 156 | 156 |
| Smoke POST | 1 | 0 | 1.00 | 21 | 21 | 21 |
| Baseline GET | 3,000 | 0 | 49.18 | 90 | 165 | 213 |
| Mixed GET | 11,000 | **6,968 (63.35%)** | 91.67 | 10,000 | **10,001** | 10,001 |
| Mixed POST | 31 | **3 (9.68%)** | 0.26 | 73 | 10,000 | 10,000 |
| Recovery GET | 300 | 0 | 10.00 | 82 | 155 | 161 |

All **6,971 mixed-phase failures were client request timeouts**. Mixed global
failure percentage was **63.19%**. Only 4,032 mixed GETs completed successfully
within the timeout: approximately **33.6 successful GET/s** over the roughly
120-second simulation. The 91.67 event/s figure therefore does not represent
successful service capacity.

The mixed log shows the first timeouts around 60 seconds, during the 100 GET/s
plateau; active virtual users continued growing during the later ramp/plateau.
This brackets a problematic workload on this machine, but does not establish a
precise sustainable maximum. The 50 GET/s baseline had no concurrent update;
it is not evidence that 50 GET/s is sustainable during processing.

## Asynchronous jobs and admission

HTTP outcomes: smoke had one `202`; mixed had one received `202`, twenty-seven
valid `409`s, and three timeouts. All busy responses referenced the mixed job's
UUID. One timed-out POST was subsequently admitted server-side, creating a third
audit entry whose `202` was never observed by the client.

| Execution UUID | Context | Audit duration (s) | Deposits/s | Final status |
| --- | --- | ---: | ---: | --- |
| `c9953c9f-1723-4da1-b93b-640fdeee31f6` | Smoke / no read load | 128.979 | 775.32 | COMPLETED |
| `8b11bc4f-0150-4d37-a2ce-41dd249ce4f7` | Mixed load | 142.010 | 704.17 | COMPLETED |
| `94d63ee9-085c-49c8-a88c-7854bd05a455` | Late admission after client timeout | 139.021 | 719.32 | COMPLETED |

Durations include admission/queueing, processing, commit, and terminal audit
overhead; they are not POST response times. The mixed job took about 10% longer
than the no-load smoke job. The third job started after the previous job ended;
the database-wide gate continued to prevent overlap.

The runner reports the third UUID in `unacknowledgedAuditIds` and fails
`acceptedIdsMatchAudit`. This records an **uncertain client outcome**, not
evidence that two workers processed simultaneously. Retrying a timed-out POST
after completion can credit balances again under the current API semantics.

## Data integrity and resources

- Before/after: **100,000 deposits / 500,005 withdrawals**. Count/hash diagnostics
  matched for every immutable deposit field and withdrawal ID/owner/amount/date.
- Every deposit's version was exactly **3**, matching three completed executions.
  No job failed; audit execution intervals did not overlap.
- Pool saturation was visible: samples showed all ten application database
  connections active, plus the sampling connection, while HTTP requests queued.
- Application threads rose from about 66 to a sampled maximum of **259**.
  RSS rose from approximately 494 MiB near startup to a sampled peak of
  **753 MiB**, then approximately 729 MiB during recovery. RSS includes native
  memory and thread stacks as well as heap.
- Logged stop-the-world GC pauses remained **below 8 ms**. The seconds-long HTTP
  delays were not explained by individual GC pauses. Live heap after collection
  grew under queued load and later fell; this short test cannot establish
  long-term memory behavior.
- Sampled database counters increased by **1,500,015 deleted rows**, exactly
  three times the withdrawal count. This is consistent with aggregate saves
  deleting/reinserting all mapped children on each execution. Immutable values
  were preserved, but the write work remains substantial.
- Buffer hits increased by approximately **2.08 billion**, versus nine additional
  physical block reads between first/last samples. The working set was mostly
  cached; resource evidence points toward query/connection demand rather than
  uncached disk reads. This does not identify a particular SQL plan as the root
  cause. No sampled deadlocks or database rollbacks occurred.
- Recovery was measured only after terminal audits and a quiet database window;
  it passed with zero failures. A recovery claim immediately after client-side
  timeouts would have been premature because server requests were still running.

## Findings and follow-up experiments

1. Profile the generated aggregate-loading SQL and deep-offset pagination with
   `EXPLAIN (ANALYZE, BUFFERS)` to explain the high logical-read cost. A bounded
   query count does not imply bounded database work per query.
2. Measure withdrawal-heavy versus zero-withdrawal datasets to isolate aggregate
   child-loading and delete/reinsert costs. The seed's `max_withdrawals` parameter
   supports that comparison.
3. Repeat finer rate steps around the overload boundary with longer plateaus,
   then run a soak at a passing rate. Keep dataset, heap, pool, and durability
   settings constant for comparisons.
4. Evaluate admission responsiveness while GET saturates the shared pool and
   request workers. Capture timeout/retry behavior explicitly: client cancellation
   did not prevent a later side effect in this test.

These measurements use a shared local machine for application, database, and
generator. They describe this dataset/configuration, not production capacity.

## Earlier run and harness correction

The preceding full run, `evaluation-20261023T160418094114Z`, also saturated:
baseline p95 201 ms, mixed GET failures 6,652/11,000, mixed p95 10,001 ms.
It exposed a gap between client timeout and later server-side admission. Its
recovery began prematurely and all 300 recovery reads timed out. That run's
pre-shutdown summary is retained as diagnostic evidence, not the final job-state
authority. Direct SQL confirmed its third job completed during graceful shutdown.

Commit `56a3432` corrects the harness by waiting for a quiet database window,
capturing final snapshots after graceful shutdown, and exposing unacknowledged
UUIDs. The authoritative rerun above uses that correction and unchanged workload
and budgets. Both runs' observed stress failures remain reported.
