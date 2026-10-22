# Time Deposit Backend Implementation Plan

Status: all seven implementation steps completed and verified on 2026-10-22. The subsequent
[Doodle-inspired hexagonal refactor](HEXAGONAL_REFACTOR_PLAN.md) is also complete and verified.
The subsequent [starter calculator Strategy refactor](CALCULATOR_REFACTOR_PLAN.md)
is complete, including compatible balance value-object encapsulation in the starter model.
The shared sources/tests are now consolidated in `domain/src/`; the separate `legacy`
module and duplicate interest policies have been removed. Both calculator APIs use the
shared strategies. Earlier verification logs below describe their historical layouts.

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
- PostgreSQL, Spring JDBC, and Flyway as the sole schema-evolution mechanism.
- The original backend work preserved `java/src/`; its subsequent calculator and
  balance-value refactor is tracked in [CALCULATOR_REFACTOR_PLAN.md](CALCULATOR_REFACTOR_PLAN.md).
- Exactly two business API operations, described in a checked-in OpenAPI contract.
- Each step is a buildable, reviewable atomic-commit boundary. No commit is created without an explicit request.

## Module boundaries

| Module | Project dependencies | Responsibility |
| --- | --- | --- |
| `domain` | None | Single compatible deposit model/calculator, immutable withdrawal/balance values, interest strategies, outbound repository port |
| `application` | `domain` | Typed command/query requests, inbound bus ports, framework-free dispatchers and handlers |
| `adapter` | None | Maven parent/aggregator for all adapter modules |
| `adapter/adapter-persistence` | `domain` | JDBC repository adapter, dedicated result-set mapper, versioned SQL migrations |
| `adapter/adapter-web` | `application`, `domain` | OpenAPI-generated interface/response DTOs, bus-delegating controller, API mapper |
| `bootstrap` | `domain`, `application`, both adapters | Composition root, transactional command-bus decorator, executable Boot application |

Domain and application production code must not depend on frameworks. Application and adapters use the shared `org.ikigaidigital.TimeDeposit` model; the update handler invokes the compatible calculator directly before saving balances. Strategy details remain encapsulated in domain.

## Steps

### 1. Maven reactor — `build(java): establish Java 25 multimodule Maven reactor`

- [x] Configure parent/aggregator, Boot 4.1.1, release 25, Surefire and Failsafe.
- [x] Introduce `legacy`, retaining existing source and test paths.
- [x] Verify existing tests and release-25 compilation; update tooling guidance.

### 2. Domain — `feat(domain): model deposits and monthly interest rules`

- [x] Model deposits and withdrawals. Subsequently simplified to the original compatible mutable `TimeDeposit`, with read-only withdrawals and string plan types; removed the duplicate immutable deposit and plan wrapper.
- [x] Separate interest eligibility/rates from application orchestration.
- [x] Preserve established arithmetic: double interest, `new BigDecimal(interest)`, HALF_UP to two decimals, then double addition without rounding the final balance.
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

- [x] Implement JDBC mapping and batched balance updates.
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
- The shared starter model now encapsulates an immutable balance value while
  preserving its public API; the backend retains its independent model.

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
