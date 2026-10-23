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

## Persistence and concurrency

- V1 already defines the quoted `"timeDeposits"` table, `withdrawals`, and its index.
  V2 extends that schema rather than replacing tables or changing applied checksums.
- Both new columns are non-null with default zero. Since legacy balances have no
  processing history, **migration treats each existing balance as opening principal**.
  If a deployment has already credited interest, populate its watermarks from an
  authoritative history before triggering the new operation; the schema cannot infer it.
- The domain model and application service contain no framework annotations.
  Bootstrap's `@Transactional` decorator covers opening/closing the cursor and every
  write, including direct calls to the inbound use case.
- Spring Data JDBC opens an ID-ordered `Stream` for `days > last_processed_days`.
  A calculation-only row mapper excludes withdrawals. Positive fetch size 100 plus
  the transaction's disabled autocommit enables PostgreSQL server-side fetching.
- The service holds at most 100 domain snapshots per write batch. JDBC batch updates
  modify only balance/watermark/version; mapped withdrawal aggregates are never saved.
- The JDBC aggregate root has `@Version`. Custom SQL also performs
  `WHERE id = ? AND version = ?`, increments the version, and requires one updated row
  per statement. A missing row, stale version, or unverifiable row count fails closed.
- No automatic retry runs inside a failed transaction. A client retry starts a fresh
  transaction and reopens the cursor. A concurrent request started after the winner's
  commit can succeed as a no-op. Concurrent inserts after cursor opening are processed
  by the next request. Other deposit writers must participate in version checking.

## Verification and AI workflow

Run `mvn test` and `mvn verify` from `java/`; integration tests require Docker and use
PostgreSQL `17.6-alpine` through Testcontainers. Existing build and Swagger instructions
in [README.md](README.md) apply. OpenAPI 3.0.0 describes both operations and HTTP 409.

Verification on 2026-10-23 used JDK 27 with Maven's release-25 target:
`mvn -q test` passed during implementation and final `mvn -q verify` passed
**164 non-integration cases and 26 PostgreSQL integration cases**, with no failures,
errors, or skips. Coverage includes:

- Every plan's incremental periods, eligibility boundaries, missed-period catch-up,
  Student expiry, rounding compatibility, and idempotency of balances/state/versions.
- 10,005 deposits with withdrawals, one observed PostgreSQL server-side cursor
  (`pg_cursors`), driver fetch size 100, autocommit off, and 101 real JDBC batches.
- Explicit result-set/statement closure on success, read failure, write failure, and
  optimistic conflict. Repeated calls with a two-connection Hikari pool end with zero
  active connections; stream close occurs before transaction completion.
- Two HTTP requests synchronized before their first writes return exactly one 204
  and one 409. A retry is a no-op, and only the winning versions/balances persist.
- Whole-transaction rollback on later-batch failure or a stale row inside a batch,
  Spring Data aggregate version checks, rejection of unverifiable JDBC row counts,
  and migration of a populated V1 schema without losing withdrawals or their index.

This work used OpenCode with `openai/gpt-6.1-sol` and the repository's `AGENTS.md`.
AI-assisted work covered migration, schedule state, ports, streaming orchestration,
JDBC mapping/batch locking, REST wiring, and behavioral tests. The user explicitly
selected fixed 30-day periods and seven atomic commits. Pre-existing pagination edits
were excluded from these commits using a separate Git index.
