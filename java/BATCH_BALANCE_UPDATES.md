# Bulk balance updates

`POST /time-deposits/balance-updates` processes every deposit in one transaction.
Success returns **204** with an empty body. A version conflict returns **409**, after
rolling back every group in the losing request. A retry loads current balances and
applies the calculator again.
`GET /time-deposits` retains its paginated response and withdrawals.

## Calculation semantics

Each successful request invokes `TimeDepositCalculator.updateBalance(List<TimeDeposit>)`
once per loaded group, using each deposit's current age and balance. Basic and Student
are eligible after day 30; Premium after day 45. Student receives no interest after
day 365, including any hypothetical earlier periods. Unknown plans receive no interest.

Every successful invocation can credit eligible deposits again, even if their ages
have not changed. There is no period schedule, processing watermark, historical
catch-up, or idempotency guarantee. The operation does not advance `days` or deduct
withdrawals. Existing balances remain the calculation inputs.

The unchanged calculator's annual-rate/12 formula, per-credit HALF_UP rounding, and
unrounded final double balance are preserved for API and direct callers alike.

## Persistence and concurrency

- V1 already defines the quoted `"timeDeposits"` table, `withdrawals`, and its index.
  V2 extends that schema rather than replacing tables or changing applied checksums.
- V2's `last_processed_days` column is obsolete and no longer mapped by the application.
  Its `version` column remains in use for optimistic locking. Existing balances are
  preserved, without principal reconstruction or reversal of previously credited interest.
- The domain model and application service contain no framework annotations.
  Bootstrap's `@Transactional` decorator covers the ID cursor, aggregate loading, and every save,
  including direct calls to the inbound use case.
- `TimeDepositBalanceJdbcRepository` extends `CrudRepository` and exposes an ID-only
  `streamAllIds()` query: `SELECT id FROM "timeDeposits" ORDER BY id`.
  JDBC fetch size 100 and the transaction's disabled autocommit enable PostgreSQL
  server-side fetching. The service closes the stream with try-with-resources.
- The service buffers at most 100 IDs, then uses inherited `findAllById(ids)` to load
  that group's complete aggregates, including withdrawals. Single-query loading avoids
  per-deposit withdrawal queries. Loaded aggregates are ordered by ID; the full dataset
  is never materialized. Every loaded deposit is passed directly to the calculator.
- The service saves each loaded group before reading the next. The bidirectional mapper
  preserves IDs, plan, age, balance, loaded version, and withdrawals before
  inherited `CrudRepository.saveAll()` persists each group. These are application-level
  groups, not a custom JDBC batch guarantee. Spring Data JDBC manages aggregate writes,
  including child-row deletion/reinsertion where needed; withdrawal data is preserved.
- The JDBC aggregate root has `@Version`. Spring Data JDBC checks the loaded version,
  increments it on save, and throws `OptimisticLockingFailureException` for stale or
  deleted aggregates. The outer transaction rolls back all earlier saves and child changes.
- No automatic retry runs inside a failed transaction. A client retry starts a fresh
  transaction and reloads aggregates. A request started after another request commits
  calculates again on the updated balances. Concurrent inserts after cursor opening
  are processed by the next request. Other deposit writers must participate in version checking.

## Verification and AI workflow

Run `mvn test` and `mvn verify` from `java/`; integration tests require Docker and use
PostgreSQL `17.6-alpine` through Testcontainers. Existing build and Swagger instructions
in [README.md](README.md) apply. OpenAPI 4.0.0 describes both operations and HTTP 409.

Coverage includes:

- Current-age eligibility boundaries, Student expiry without catch-up, rounding
  compatibility, and repeated successful requests crediting interest again.
- 10,005 IDs fetched through one observed PostgreSQL server-side cursor, fetch size 100,
  autocommit off, 101 bounded aggregate reads, and 101 application-level save groups.
  Exact withdrawal IDs, ownership, amounts, and dates survive saves.
- Actual result-set/statement closure on success and ID-read, aggregate-load, write,
  and conflict failures. Repeated calls with a two-connection Hikari pool end with zero
  active connections; stream closure happens before transaction completion.
- Two HTTP requests synchronized before their first writes return exactly one 204
  and one 409. Only the winning versions/balances persist; a successful retry calculates again.
- Whole-transaction rollback on later-group failure or a stale/deleted aggregate,
  Spring Data version checks and increments, decimal precision,
  and migration of a populated V1 schema without losing withdrawals or their index.

This work used OpenCode with `openai/gpt-6.1-sol` and the repository's `AGENTS.md`.
The user's latest instruction removes incremental processing entirely, superseding
the earlier fixed 30-day schedule. AI-assisted work covers the domain/application
simplification, all-ID streaming query, mapping cleanup, behavioral tests, and
documentation. Historical implementation details remain in `AI_WORKFLOW.md` and
`IMPLEMENTATION_PLAN.md` as records of superseded designs.
