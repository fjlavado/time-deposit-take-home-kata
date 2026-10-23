# Background balance-update jobs

Balance updates use UUID execution IDs and a minimal internal `balance_update_executions`
audit table (Flyway V4). Each accepted attempt creates one row: `id`, `status`,
`started_at`, and nullable `finished_at`. `COUNT(*)` counts accepted attempts; group by
`status` to distinguish completed and failed attempts. Busy requests create no rows.

## Database-backed admission

A partial unique index on a constant allows at most one row with status `STARTED` or
`RUNNING`. Reservation uses `INSERT ... ON CONFLICT DO NOTHING` in a short,
`REQUIRES_NEW`, `READ_COMMITTED` transaction. A losing caller reads the active UUID;
if that job finished between the INSERT and SELECT, it retries reservation. This
works across threads, independent handlers, and application instances sharing the
database. No JVM lock or check-then-insert assumption is used.

The reservation commits before executor submission. Queued work occupies the same
slot as running work. The worker invokes the existing transactional balance use case;
all ID streaming, bounded aggregate reads, calculation, and saves share its worker-thread
transaction. After commit, a separate short transaction records `COMPLETED`; after
rollback it records `FAILED`. A terminal status releases the database slot. Executor
rejection also records `FAILED`. Another accepted attempt may credit interest again.

This is an executor-backed kata implementation: graceful shutdown waits for submitted
work. It has no automatic restart/replay. A process crash or audit-write failure can
leave an active record; an operator must investigate the persisted balances before
marking that record failed with a finish timestamp. Automatically expiring it could
permit a second execution while the original worker is still updating balances.

## Why there is no execution-log endpoint

The assignment requires **exactly two business endpoints**. The new table is an
internal execution audit and database admission gate, not a new public resource.
Adding execution lookup/history/polling would expand the kata into job-management
functionality. Counts and outcomes can be inspected directly in the database:

```sql
SELECT status, count(*) FROM balance_update_executions GROUP BY status;
SELECT id, status, started_at, finished_at
FROM balance_update_executions ORDER BY started_at DESC;
```

The POST response identifies the accepted or already-active UUID. Once a job ends,
another POST starts a **new** attempt; POST is not a completion-polling operation.
GET remains the existing paginated deposit retrieval operation.

## HTTP contract and wiring

`POST /time-deposits/balance-updates` dispatches `StartBalanceUpdateJobCommand`.
The handler delegates to `BalanceUpdateJob`; bootstrap supplies the background runner.
The command bus starts no outer transaction for this command, so a request does not
hold a connection while reservation needs another one. The worker calls the existing
transactional use case. The internal synchronous processing command remains available
to direct callers. A managed executor drains accepted work before datasource shutdown.

- **202**: `{ "jobId": "<uuid>", "status": "STARTED", "message": "Balance update job accepted." }`
- **409**: `{ "jobId": "<same-active-uuid>", "status": "RUNNING", "message": "A balance update job is already running." }`

STARTED is an acceptance acknowledgement even if a very small job finishes before
the response arrives. RUNNING also covers queued work. These are snapshots at admission,
not promises about the state when a client receives the response. GET shows committed
balances and may still show old values immediately after a 202. OpenAPI 5.0.0 describes
both bodies; there is no Location header pointing to a nonexistent status resource.

## Verification

Run `mvn test` and `mvn verify` from `java/` with JDK 25+ and Maven. Integration
tests require Docker and use PostgreSQL 17.6 through Testcontainers. Coverage includes
simultaneous calls through independent handlers/runners, database enforcement even
outside the handler, queued/running execution reuse, UUID and timestamp persistence,
completion/repeated credits, later-group rollback with a surviving failed audit, and
executor rejection releasing the reservation.

The database/job foundation passed `mvn -q test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn -q verify` on JDK 27. The initial
verification identified an existing assertion pinned to schema V3; it was updated
to V4, and the complete verification then passed.

The completed asynchronous API passed `mvn -q clean test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn -q verify`: **160 non-integration cases
and 35 PostgreSQL integration cases**, with no failures, errors, or skips. HTTP tests
hold the worker with a latch to verify prompt acceptance, same-UUID conflicts, old
balances before commit, single crediting, and later-group rollback with a failed audit.
The streaming HTTP suite retains its two-connection pool to catch connection starvation.

AI-assisted implementation uses OpenCode, `openai/gpt-6.1-sol`, and `java/AGENTS.md`.
The user explicitly requested UUID IDs, database race prevention, exactly two public
operations, a minimal execution log, and atomic commits. No additional setup is needed.
