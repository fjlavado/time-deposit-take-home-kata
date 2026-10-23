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

AI-assisted implementation uses OpenCode, `openai/gpt-6.1-sol`, and `java/AGENTS.md`.
The user explicitly requested UUID IDs, database race prevention, exactly two public
operations, a minimal execution log, and atomic commits. No additional setup is needed.
