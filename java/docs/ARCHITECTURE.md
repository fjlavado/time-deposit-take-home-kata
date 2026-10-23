# Hexagonal architecture and ownership

All module/source paths and commands here are relative to `java/`. Operational
instructions are in [README.md](README.md); agent rules remain in
[AGENTS.md](../AGENTS.md).

## Dependency direction

```text
bootstrap -> adapter-web -----------+
          -> adapter-runtime -------+-> application -> domain
          -> adapter-persistence ---+
```

Bootstrap can also depend directly on application/domain to construct objects.
Adapters depend inward on application contracts and domain values; no adapter
depends on another adapter. Domain and application have no production framework
dependencies. Only bootstrap packages an executable Spring Boot application.

| Owner | Responsibilities |
| --- | --- |
| `domain` | `domain.interest.TimeDeposit`, balance/plan values, withdrawals, `services.TimeDepositCalculator`, and sealed `services.interest` strategies/resolver |
| `application` | Commands/queries, inbound use-case/bus ports, handlers/dispatchers, `port.out` repository/task-submission ports, pagination values and job orchestration/state |
| `adapter/adapter-web` | Generated HTTP types, controller, API mapping and CORS policy |
| `adapter/adapter-persistence` | JDBC records/repositories, aggregate mapping, SQL/migrations, repository-port implementations and independent audit transactions |
| `adapter/adapter-runtime` | Executor-backed task submission and Spring transactional use-case/command-bus decorators |
| `bootstrap` | Entry point, object composition, framework configuration, managed executor lifecycle and end-to-end/architecture verification |

`TimeDepositCalculator` is a plain domain object constructed while composing
`UpdateDepositBalancesService`; it is never registered as a Spring bean. Its public
constructors, in-place update API, rounding and eligibility behavior are preserved.
Strategies remain encapsulated by domain. Controllers use inbound buses and
request/result types, never outbound repositories or calculator services.

## Balance-update execution

1. Web dispatches `StartBalanceUpdateJobCommand` through the inbound command bus.
2. The runtime transactional bus bypasses its outer transaction for submission.
3. The handler invokes application `BackgroundBalanceUpdateJob`, which reserves a
   UUID through `BalanceUpdateExecutionRepository`.
4. Persistence's `TransactionalBalanceUpdateExecutionRepository` wraps the JDBC
   implementation in short `REQUIRES_NEW` / `READ_COMMITTED` transactions. A
   reservation is committed before returning. The existing partial unique index
   and atomic insert-or-return-active behavior prevent overlapping executions
   across application instances.
5. The job submits through `BackgroundTaskExecutor`; runtime's
   `ExecutorBackgroundTaskExecutor` delegates to the managed executor. A busy
   reservation returns the existing UUID without submitting. Rejection records
   `FAILED` before propagating the submission failure.
6. On the worker, the job records `RUNNING` and invokes the decorated
   `UpdateDepositBalancesUseCase`. Its transaction covers the ID cursor and every
   bounded group load/calculate/save. The application service buffers at most
   100 IDs, closes the stream, and preserves the calculator's arithmetic.
7. After the processing transaction commits, the application records `COMPLETED`.
   On failure, processing rolls back before a separate audit records `FAILED`.
   Audit transitions commit independently of a caller's transaction.

The executor is drained before the datasource closes. No automatic active-job
expiry or replay is introduced. See [background jobs](BACKGROUND_BALANCE_UPDATES.md)
and [bulk calculation semantics](BATCH_BALANCE_UPDATES.md).

## Retrieval

Web dispatches `RetrieveTimeDepositsQuery` through the inbound query bus. Its
handler uses the application-owned read repository and pagination values.
Persistence translates Spring Data paging and maps complete aggregates; web maps
domain values to generated response types. Query dispatch does not open a
transaction. The API remains exactly the two operations in [OpenAPI](openapi.yaml).

## Enforcement and verification

- Maven compilation boundaries prevent core-to-adapter dependencies.
- ArchUnit checks core purity, inward dependencies, isolation between all three
  adapters, controller bus use, command/query separation, the actual strategy
  package, and configuration-only bootstrap top-level classes.
- The transaction-bus unit tests live in `adapter-runtime`; full-context tests
  remain in bootstrap. They verify no calculator bean, transaction-before-read,
  multi-group rollback, database-wide admission, independently committed audits,
  worker-thread processing, HTTP behavior and withdrawal preservation.
- `mvn -q clean test` and
  `DOCKER_HOST=unix:///var/run/docker.sock mvn -q -Pperformance verify` passed on
  JDK 27 with release 25: all nine architecture checks and 36 PostgreSQL
  integration cases passed without failures, errors or skips. The performance
  profile builds its harness; it does not run a stress evaluation.

The ownership correction was delivered as `0a4dc1f` (application contracts) and
`988ab68` (runtime extraction). Historical design steps remain in
[the refactoring plan](HEXAGONAL_REFACTOR_PLAN.md); the actual tools and prompts
are recorded in [AI_WORKFLOW.md](AI_WORKFLOW.md).
