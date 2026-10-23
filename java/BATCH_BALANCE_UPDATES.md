# Incremental balance updates

`POST /time-deposits/balance-updates` processes all pending deposits in one transaction.
Success returns **204** with an empty body. A version conflict returns **409**, after
rolling back every batch in the losing request. Retrying the request is safe.
`GET /time-deposits` retains its paginated response and withdrawals.

## Interest schedule

The agreed interpretation is a fixed 30-day month because the kata stores an age in
days, without an opening date. Basic and Student credit on days 31, 61, 91, etc.;
Premium credits on days 46, 76, 106, etc. Student's last scheduled credit is day 361;
no new credit is due after day 365. An expired Student with unprocessed earlier
periods still receives those earlier credits. Unknown plans receive no interest.

`last_processed_days` is the highest age examined, including ineligible days.
Advancing from day 31 to 60 does not earn another credit; advancing to 61 does.
Advancing over multiple periods compounds each missing credit in chronological
order. Previously examined periods are never recalculated. Decreasing `days` does
not rewind the watermark. The operation does not advance `days` itself.

For each due period, the application passes the period's age and current compounded
balance to the unchanged `TimeDepositCalculator.updateBalance(List<TimeDeposit>)`.
Its annual-rate/12 formula, per-credit HALF_UP rounding, and unrounded final double
balance are preserved. Calculator callers outside this batch API retain the original
per-invocation behavior.

`InterestPeriodSchedule` is an immutable domain value object containing `PlanType`.
It selects pending period days from the current age and processing watermark,
including the Student cutoff and overflow-safe interval arithmetic.
`TimeDeposit.pendingInterestDays()` delegates to this value object.

## Persistence and concurrency

- V1 already defines the quoted `"timeDeposits"` table, `withdrawals`, and its index.
  V2 extends that schema rather than replacing tables or changing applied checksums.
- Both new columns are non-null with default zero. Since legacy balances have no
  processing history, **migration treats each existing balance as opening principal**.
  If a deployment has already credited interest, populate its watermarks from an
  authoritative history before triggering the new operation; the schema cannot infer it.
- The domain model and application service contain no framework annotations.
  Bootstrap's `@Transactional` decorator covers the ID cursor, aggregate loading, and every save,
  including direct calls to the inbound use case.
- `TimeDepositBalanceJdbcRepository` extends `CrudRepository` and exposes an ID-only
  `streamPending()` query: `SELECT id FROM "timeDeposits" WHERE days > last_processed_days ORDER BY id`.
  JDBC fetch size 100 and the transaction's disabled autocommit enable PostgreSQL
  server-side fetching. The service closes the stream with try-with-resources.
- The service buffers at most 100 IDs, then uses inherited `findAllById(ids)` to load
  that group's complete aggregates, including withdrawals. Single-query loading avoids
  per-deposit withdrawal queries. Loaded aggregates are ordered by ID; the full dataset
  is never materialized. The service rechecks `days > lastProcessedDays` because another
  transaction may have processed a streamed ID before its aggregate is loaded.
- The service saves each loaded group before reading the next. The bidirectional mapper
  preserves IDs, plan, age, balance, watermark, loaded version, and withdrawals before
  inherited `CrudRepository.saveAll()` persists each group. These are application-level
  groups, not a custom JDBC batch guarantee. Spring Data JDBC manages aggregate writes,
  including child-row deletion/reinsertion where needed; withdrawal data is preserved.
- The JDBC aggregate root has `@Version`. Spring Data JDBC checks the loaded version,
  increments it on save, and throws `OptimisticLockingFailureException` for stale or
  deleted aggregates. The outer transaction rolls back all earlier saves and child changes.
- No automatic retry runs inside a failed transaction. A client retry starts a fresh
  transaction and reloads aggregates. A concurrent request started after the winner's
  commit can succeed as a no-op. Concurrent inserts after cursor opening are processed
  by the next request. Other deposit writers must participate in version checking.

## Verification and AI workflow

Run `mvn test` and `mvn verify` from `java/`; integration tests require Docker and use
PostgreSQL `17.6-alpine` through Testcontainers. Existing build and Swagger instructions
in [README.md](README.md) apply. OpenAPI 3.0.0 describes both operations and HTTP 409.

Verification on 2026-10-23 used JDK 27 with Maven's release-25 target:
`mvn test` and `DOCKER_HOST=unix:///var/run/docker.sock mvn verify` passed
**162 non-integration cases and 29 PostgreSQL integration cases**, with no failures,
errors, or skips. Coverage includes:

- Every plan's incremental periods, eligibility boundaries, missed-period catch-up,
  Student expiry, rounding compatibility, and idempotency of balances/state/versions.
- 10,005 IDs fetched through one observed PostgreSQL server-side cursor, fetch size 100,
  autocommit off, 101 bounded aggregate reads, and 101 application-level save groups.
  Exact withdrawal IDs, ownership, amounts, and dates survive saves.
- Actual result-set/statement closure on success and ID-read, aggregate-load, write,
  and conflict failures. Repeated calls with a two-connection Hikari pool end with zero
  active connections; stream closure happens before transaction completion.
- Two HTTP requests synchronized before their first writes return exactly one 204
  and one 409. A retry is a no-op, and only the winning versions/balances persist.
- Whole-transaction rollback on later-group failure or a stale/deleted aggregate,
  Spring Data version checks and increments, decimal precision,
  and migration of a populated V1 schema without losing withdrawals or their index.

This work used OpenCode with `openai/gpt-6.1-sol` and the repository's `AGENTS.md`.
AI-assisted work covered migration, schedule state, ports, the initial streaming
implementation, and the subsequent user-requested aggregate-persistence refactor.
The latest refinement restores an ID-only pending stream while retaining inherited
CRUD operations for bounded aggregate loads and saves. It supersedes the earlier
eager-loading choice, preserves the schedule value object, and updates behavioral tests
and documentation. The user selected fixed 30-day periods and bounded ID-driven processing.
Historical implementation and commit details are recorded in `AI_WORKFLOW.md`.
