# Time Deposit Backend Implementation Plan

Status: all seven implementation steps completed and verified on 2026-10-22. The subsequent
[Doodle-inspired hexagonal refactor](HEXAGONAL_REFACTOR_PLAN.md) is also complete and verified.
The subsequent [starter calculator Strategy refactor](CALCULATOR_REFACTOR_PLAN.md)
is complete, including compatible balance value-object encapsulation in the starter model.
The shared sources/tests are now consolidated in `domain/src/`; the separate `legacy`
module and duplicate interest policies have been removed. Both calculator APIs use the
shared strategies. Earlier verification logs below describe their historical layouts.
The persistence adapter now uses Spring Data JDBC table records and repository proxies;
the migration and verification checkpoint is recorded below.
Subsequent CRUD/pagination work keeps Spring Data in persistence, replaces unbounded
domain reads with `search(PageRequest)`, exposes paginated GET, and updates balances
page by page within one transaction.

Current supplied refactor: the deposit moved to `domain.interest.TimeDeposit`, the calculator
to `services.TimeDepositCalculator`, and plan/balance values to `domain.interest.vo`.
Strategies are sealed with declared `PlanType` keys, and rounding now uses
`BigDecimal.valueOf(interest)`. Tests/documentation follow these changes. The user approved
three minimal fixes for production accessor/registry/mapping mismatches, and verification
now passes after singleton test cleanup: **153 non-integration and 15 PostgreSQL
integration cases**, with no failures,
errors, or skips (`mvn test` and `DOCKER_HOST=unix:///var/run/docker.sock mvn verify`,
JDK 27 with release 25). Earlier logs remain historical.

## Implementation commits

The user explicitly requested commits after implementation. Each checkpoint below was verified from an exported Git index snapshot, containing only files staged for that commit, before committing.

| Step | Commit | Summary |
| --- | --- | --- |
| 1 | `f384f10` | `build(java): establish Java 25 multimodule Maven reactor` |
| 2 | `262ee7a` | `feat(domain): model deposits and monthly interest rules` |
| 3 | `27b1d80` | `feat(domain): define deposit repository port` |
| 4 | `8154a41` | `feat(application): define deposit use case ports` |
| 5 | `6191319` | `feat(application): implement deposit use cases` |
| 6 | `1e00eac` | `feat(persistence): add Flyway-managed PostgreSQL adapter` |
| 7 | `b2f84f3` | `feat(api): expose deposit retrieval and balance updates` |

Operational instructions, this tracking document, demonstration SQL, and the AI-workflow record are delivered in a separate documentation commit.

## Constraints

- Java 25 bytecode (`--release 25`), compilable with the installed JDK 27.
- Maven multi-module reactor with Spring Boot 4.1.1 dependency management.
- PostgreSQL, Spring Data JDBC, and Flyway as the sole schema-evolution mechanism.
- The original backend work preserved `java/src/`; its subsequent calculator and
  balance-value refactor is tracked in [CALCULATOR_REFACTOR_PLAN.md](CALCULATOR_REFACTOR_PLAN.md).
- Exactly two business API operations, described in a checked-in OpenAPI contract.
- Each step is a buildable, reviewable atomic-commit boundary. No commit is created without an explicit request.

## Module boundaries

| Module | Project dependencies | Responsibility |
| --- | --- | --- |
| `domain` | None | Single deposit model/calculator service, immutable plan/balance/withdrawal values, sealed interest strategies, outbound repository port |
| `application` | `domain` | Typed command/query requests, inbound bus ports, framework-free dispatchers and handlers |
| `adapter` | None | Maven parent/aggregator for all adapter modules |
| `adapter/adapter-persistence` | `domain` | Spring Data JDBC table records/repositories, domain adapter/mapper, versioned SQL migrations |
| `adapter/adapter-web` | `application`, `domain` | OpenAPI-generated interface/response DTOs, bus-delegating controller, API mapper |
| `bootstrap` | `domain`, `application`, both adapters | Composition root, transactional command-bus decorator, executable Boot application |

Domain and application production code must not depend on frameworks. Application and adapters use the shared `org.ikigaidigital.domain.interest.TimeDeposit` model; the update handler invokes `org.ikigaidigital.services.TimeDepositCalculator` directly before saving balances. Strategy details remain encapsulated in domain. Plan arguments/getters use `PlanType`; HTTP and database names remain strings.

## Steps

### 1. Maven reactor — `build(java): establish Java 25 multimodule Maven reactor`

- [x] Configure parent/aggregator, Boot 4.1.1, release 25, Surefire and Failsafe.
- [x] Introduce `legacy`, retaining existing source and test paths.
- [x] Verify existing tests and release-25 compilation; update tooling guidance.

### 2. Domain — `feat(domain): model deposits and monthly interest rules`

- [x] Model deposits and withdrawals. Subsequently consolidated to one mutable `TimeDeposit` with read-only withdrawals; the latest supplied refactor introduces `PlanType` values rather than raw string plan arguments.
- [x] Separate interest eligibility/rates from application orchestration.
- [x] Original arithmetic preservation: double interest, `new BigDecimal(interest)`, HALF_UP, then double addition without final rounding. The supplied refactor now uses `BigDecimal.valueOf(interest)`; tests assert its changed half-cent results.
- [x] Cover basic (`days > 30`, 1%/12), student (`30 < days < 366`, 3%/12), premium (`days > 45`, 5%/12), unknown plans, rounding, and repeated updates.
- [x] Do not change age or deduct withdrawals.

### 3. Outbound ports — `feat(domain): define deposit repository port`

- [x] Define a domain repository for reading deposits with withdrawals and saving balances.
- [x] Keep all port signatures free of framework and persistence types.

### 4. Inbound ports — `feat(application): define deposit use case ports`

- [x] Define retrieval and bulk-update use-case interfaces in `application`.
- [x] Use only Java/domain types; verify dependency direction.

### 5. Interactors — `feat(application): implement deposit use cases`

- [x] Implement constructor-injected retrieval and load/calculate/persist services.
- [x] Test empty collections, bulk persistence, and preservation of age/withdrawals with a repository fake.
- [x] Keep transactions outside the application layer.

### 6. Persistence — `feat(persistence): add Flyway-managed PostgreSQL adapter`

- [x] Implement JDBC mapping and batched balance updates (subsequently replaced by Spring Data JDBC mapping and balance-only modifying queries; see migration below).
- [x] Load withdrawals without per-deposit queries.
- [x] Add `db/migration/V1__create_time_deposit_schema.sql` with required tables, keys, constraints, decimal/date columns, and foreign-key index.
- [x] Run/validate Flyway migrations at startup; introduce changes through new immutable versioned migrations.
- [x] Introduce bootstrap wiring, executable packaging, and a transaction decorator around the entire bulk update.
- [x] Provide local PostgreSQL startup/configuration.
- [x] Add PostgreSQL Testcontainers coverage for clean migration, mappings, updates, and rollback.

### 7. REST — `feat(api): expose deposit retrieval and balance updates`

- [x] `GET /time-deposits`: 200 with `id`, `planType`, `balance`, `days`, `withdrawals`.
- [x] `POST /time-deposits/balance-updates`: apply/persist interest and return 204.
- [x] Withdrawal responses include `id`, `amount`, and `date`.
- [x] Controllers depend on inbound ports; use dedicated response DTOs.
- [x] Check in an OpenAPI contract with exactly these operations; document external Swagger UI usage.
- [x] Verify HTTP response shape, persisted results, and contract/mapping agreement.

## Final verification and delivery

- [x] Run `mvn test` and `mvn verify` (Docker required for PostgreSQL integration tests).
- [x] Enforce architecture boundaries and absence of dependencies on legacy code.
- [x] Confirm original source/test files remain unchanged.
- [x] Document build, database startup, application startup, Flyway workflow, endpoints, and Swagger usage.
- [x] Record actual AI tools/model, rules, setup, and assisted work.
- [x] Report verification blockers accurately; do not mark blocked checks as passed.

## Subsequent starter refactor

- [x] Separately plan and implement calculator Strategy extraction and behavioral
  coverage in [CALCULATOR_REFACTOR_PLAN.md](CALCULATOR_REFACTOR_PLAN.md).
- The single shared model encapsulates an immutable balance value. The latest
  package/typed-plan changes require caller migration; see the current README.

## Spring Data JDBC migration — 2026-10-23

- [x] Replace the adapter's `spring-jdbc` dependency with `spring-data-jdbc` and use `spring-boot-starter-data-jdbc` in bootstrap.
- [x] Add annotated `TimeDepositTable` and `WithdrawalTable` records, with decimal/date types and explicit camel-case mappings; map withdrawals as aggregate children with `@MappedCollection(idColumn = "timeDepositId")`.
- [x] Introduce the Spring Data aggregate repository with a base `findAll()` read, ordered row locking, and balance-only `@Modifying` queries.
- [x] Replace template-based reads/writes and result-set extraction with repository delegation and `TimeDepositPersistenceMapper`.
- [x] Load deposits and mapped withdrawals in one query with single-query loading, replacing the initially implemented separate bulk reads; sort aggregate/withdrawal IDs in memory. Use one modifying statement per deposit rather than template batch updates.
- [x] Explicitly scan JDBC repositories and enable single-query loading in bootstrap; retain transaction decoration around the entire command.
- [x] Adapt PostgreSQL adapter tests to actual repository proxies; assert a single read query and strengthen ordering, withdrawal ownership, and unchanged withdrawal ID/amount/date assertions. Verify bootstrap enables single-query loading.
- [x] Update architecture/dependency documentation, project guidance, and the AI workflow record.

Verification: `mvn -B clean test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify` passed on JDK 27 with
release 25: 155 non-integration cases and 11 PostgreSQL integration cases, with
no failures, errors, or skips. Coverage includes migration validation, empty reads,
mapped-collection single-query loading, balance-only writes, HTTP behavior, rollback,
and concurrent updates. The focused adapter check
`DOCKER_HOST=unix:///var/run/docker.sock mvn -q -pl adapter/adapter-persistence -am verify`
also passed after normalizing absent mapped withdrawals to an empty set.

## Inherited CRUD and paginated search — 2026-10-23

- [x] Preserve the user's `CrudRepository<TimeDepositTable, Integer>` inheritance without redeclaring `findAll`.
- [x] Keep domain/application free of Spring Data, as selected by the user; introduce domain `PageRequestVO`/`Page` values and replace unbounded reads with `search(PageRequestVO)`.
- [x] Use inherited `PagingAndSortingRepository.findAll(Pageable)` for an ID-only table view, then inherited `CrudRepository.findAllById` for mapped-collection aggregate loading.
- [x] Keep query count bounded: at most three reads per populated page (IDs, optional count, mapped aggregates), with no per-deposit child queries. No schema changes are required.
- [x] Expose `page`/`size` parameters with defaults 0/20 and a generated page response containing `content`, `page`, `size`, `totalElements`, and `totalPages`. Version the OpenAPI contract as 2.0.0 for the changed GET response shape.
- [x] Add the validation provider required by generated pagination constraints.
- [x] Lock once before iterating pages of 100 deposits in the update handler; retain a single transaction and balance-only persistence.
- [x] Cover default/explicit pagination, empty/full/partial/out-of-range pages, withdrawal mapping, JDBC query counts, 201/205-deposit updates, and second-page rollback.

Verification: focused PostgreSQL adapter verification passed after the direct paged
aggregate query exposed child-query fallback and JDBC statement counting was expanded
to include the plain count query. `mvn -B clean test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify` then passed on JDK 27 with
release 25: **158 non-integration cases and 14 PostgreSQL integration cases**, with
no failures, errors, or skips. Architecture checks continue to enforce framework-free
domain/application modules and exactly two HTTP business operations.

## Verification log

- Step 1: `mvn -B verify` passed on JDK 27; compiler reported `release 25`. The unchanged legacy test passed.
- Step 2: `mvn -q -pl domain -am verify` passed (25 domain test cases).
- Steps 3–4: `mvn -q -pl application -am verify` passed with the repository and use-case contracts.
- Step 5: `mvn -q -pl application -am verify` passed with 3 application tests and 25 domain cases.
- Step 6: `DOCKER_HOST=unix:///var/run/docker.sock mvn -q verify` passed, including 4 repository and 3 bootstrap PostgreSQL integration tests (migration, mapping, rollback, and concurrent updates).
- Final: `mvn -q clean test` and `DOCKER_HOST=unix:///var/run/docker.sock mvn -q verify` passed. There are 37 unit/controller/contract/architecture/legacy cases and 10 real PostgreSQL integration cases; no failures, errors, or skips.
- Step 7: real HTTP GET/POST tests passed, including withdrawals, persistence, repeated updates, empty databases, and Swagger-origin CORS. The OpenAPI parser and controller-mapping comparison passed.
- `docker compose config --quiet`, `git diff --check`, and `git diff --exit-code -- java/src` passed. Original classes and tests are unchanged. Operational and AI-workflow documentation is in `README.md` and `AI_WORKFLOW.md`.
- Environment: the inherited remote `DOCKER_HOST` timed out, but the local daemon responds. Integration commands use `DOCKER_HOST=unix:///var/run/docker.sock` without changing global configuration.
- Commit verification: exported each staged checkpoint using `git checkout-index` into `/tmp/opencode/time-deposit-commit-check/` and ran `mvn -q verify` against that snapshot. Steps 6–7 used the local Docker override. Every checkpoint passed; the complete API checkpoint passed all 47 cases.
- Adapter module refactor: moved both adapters under the `adapter` Maven parent/aggregator. `mvn -B clean test` and `DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify` passed on JDK 27 with release 25: all 37 unit/controller/contract/architecture/legacy cases and 10 PostgreSQL integration cases passed without failures, errors, or skips. The OpenAPI resource and Flyway migrations load successfully from the nested modules.
- Contract-first web refactor: OpenAPI Generator 7.26.0 now generates the Spring API interface and response models from `docs/openapi.yaml` during Maven's `generate-sources` phase. The controller implements the generated interface; handwritten response DTOs and HTTP mappings were removed. `mvn -B -pl adapter/adapter-web -am generate-sources`, `mvn -B clean test`, and `DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify` passed on JDK 27 with release 25. All 37 unit/controller/contract/architecture/legacy cases and 10 PostgreSQL integration cases passed without failures, errors, or skips, including generated mapping/status inheritance, response fields, decimal/date serialization, and persisted updates.
- Doodle-inspired hexagonal refactor: `mvn -B -pl application -am verify`, `mvn -B clean test`, and `DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify` passed on JDK 27 with release 25. The final reactor passed 44 unit/controller/contract/architecture/legacy cases and 11 PostgreSQL integration cases without failures, errors, or skips. The new buses, handlers, adapter mappers, transaction decoration, and architecture rules are tracked with commit checkpoints in [HEXAGONAL_REFACTOR_PLAN.md](HEXAGONAL_REFACTOR_PLAN.md).

## Controller-to-database cleanup — 2026-10-23

- [x] Trace both endpoints and verify obsolete-method references against production callers and tests.
- [x] Remove the superseded locking and individual balance-write methods from the retrieval port, JDBC adapter, and aggregate repository.
- [x] Remove obsolete test-double methods/counters, unused transaction-template setup, the unused static import, and the test-only `Page.hasNext()` helper.
- [x] Move decimal precision and metadata/withdrawal-preservation assertions to batch persistence integration coverage, including watermark updates and version increments.
- [x] Update current persistence documentation and record the AI-assisted workflow.
- [x] Run `mvn test`, `DOCKER_HOST=unix:///var/run/docker.sock mvn verify`, and `git diff --check`.

Verification passed on JDK 27 with release 25: **164 non-integration cases and
26 PostgreSQL integration cases**, with no failures, errors, or skips. The executable
application was packaged successfully. Coverage includes bounded pagination reads,
decimal precision, idempotency, cursor closure, rollback, and HTTP concurrency.

## Aggregate CRUD persistence and schedule value object — 2026-10-23

- [x] Use inherited `CrudRepository.findAll()` on the balance repository to load full aggregates with withdrawals; filter pending deposits and sort by ID in the adapter.
- [x] Add domain-to-table mapping that preserves all aggregate fields and loaded versions, and delegate persistence to inherited `saveAll()` and `@Version` optimistic locking.
- [x] Replace the streaming port with eager `findPending()`/`saveAll()` operations and retain application-level save groups of 100 in one transaction.
- [x] Remove custom query/row mapper, JDBC batch SQL/row-count checks, cursor configuration, and obsolete driver-specific test instrumentation.
- [x] Extract plan-based due-day calculations into immutable `InterestPeriodSchedule`; leave deposit state in `TimeDeposit` and monetary calculation in the existing calculator.
- [x] Migrate tests to aggregate round trips, withdrawal preservation, real framework-managed stale/deleted-row failures, rollback, idempotency, and HTTP concurrency.
- [x] Update current documentation, project guidance, and AI workflow records.
- [x] Run `mvn test` and `DOCKER_HOST=unix:///var/run/docker.sock mvn verify`.

Verification passed on JDK 27 with release 25: **160 non-integration cases and
27 PostgreSQL integration cases**, without failures, errors, or skips. Packaging
succeeded. Real-database coverage includes 10,005 aggregates with withdrawals,
101 application save groups, preserved child data, and whole-transaction rollback.

## ID-only streaming and bounded aggregate loading — 2026-10-23

The user's follow-up supersedes eager `findAll()` loading from the preceding checkpoint.

- [x] Restore the pending predicate and ID ordering in `streamPending()`, selecting only `id` and returning `Stream<Integer>`.
- [x] Consume at most 100 IDs at a time, load each group's full deposits/withdrawals through inherited `findAllById()`, and persist via inherited `saveAll()`.
- [x] Recheck loaded processing watermarks to avoid saving deposits already processed since cursor creation.
- [x] Restore fetch size 100 and an outer transaction for PostgreSQL server-side fetching; close the ID stream before transaction completion on success/failure.
- [x] Verify lazy ID consumption, bounded aggregate loads, actual cursor behavior/closure, full withdrawal preservation, and rollback across failed read/load/save groups.
- [x] Update current documentation/guidance and run `mvn test`, `DOCKER_HOST=unix:///var/run/docker.sock mvn verify`, and `git diff --check`.

Verification passed on JDK 27 with release 25: **162 non-integration cases and
29 PostgreSQL integration cases**, without failures, errors, or skips. The large
fixture observes one ID-only cursor and 101 aggregate reads/save groups for 10,005
deposits, with each group bounded to 100. Packaging succeeded.
