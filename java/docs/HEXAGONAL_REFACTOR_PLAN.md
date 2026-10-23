# Doodle-Inspired Hexagonal Refactoring Plan

See [ARCHITECTURE.md](ARCHITECTURE.md) for current ownership. Commands and source
paths in this historical plan are relative to `java/` unless otherwise specified.

## Current ownership correction — 2026-10-23

The historical steps below originally placed runtime implementations in bootstrap.
The stricter follow-up moves repository ports, pagination and execution state to
application, and makes `BackgroundBalanceUpdateJob` framework-free there.
`adapter-runtime` owns executor submission and transactional decorators;
`adapter-persistence` owns independent execution-audit transactions; `adapter-web`
owns CORS policy. Bootstrap is composition/lifecycle only. The calculator remains
a plain domain object and is not registered as a Spring bean. ArchUnit now targets
the actual `services.interest` strategy package and enforces these boundaries.

This completed plan records the original refactor. The later domain consolidation
moved shared sources/tests from `java/src/` into `java/domain/src/`, removed `legacy`,
and replaced duplicate interest policies with the shared Strategy implementation.
See [README.md](README.md) for the current module layout.

The 2026-10-23 supplied typed/sealed refactor further moves the model to
`org.ikigaidigital.domain.interest`, value objects to `domain.interest.vo`, and the
calculator to `org.ikigaidigital.services`. Architecture tests include services in
domain, allow application handlers to call the calculator, and allow web mapping of
the relocated model while retaining the ban on web access to repository ports and
calculator services. Current test/documentation updates follow that layout. After the
user approved three minimal production compilation fixes, `mvn test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn verify` passed: 179 non-integration cases
and 15 PostgreSQL integration cases, with no failures, errors, or skips. Subsequent
singleton test cleanup reduced the non-integration suite to 153 passing cases;
all 15 integration cases still pass. The completed
steps and passing logs below describe the original refactor snapshot.

## Goal and constraints

Apply Doodle's synchronous command/query dispatch pattern to this Java backend,
preserving the Maven modules, two HTTP operations, interest arithmetic, withdrawals,
ordered update locking, and transaction consistency. Domain and application code
remain framework-free. Original `java/src/` classes and tests remain unchanged.

```text
adapter-web: controller -> CommandBus / QueryBus (application inbound ports)
application: typed requests -> dispatchers -> dedicated handlers
domain: models, interest policies, outbound repository port
adapter-persistence: JDBC repository adapter -> persistence mapper -> PostgreSQL
bootstrap: explicit wiring and transactional command-bus decorator
legacy: original starter implementation and tests
```

## Steps

### 1. Define command/query contracts
- [x] Add result-typed Command/Query, handler contracts, and inbound bus ports.
- [x] Add UpdateTimeDepositBalancesCommand and RetrieveTimeDepositsQuery records.
Acceptance: contracts use only Java/domain types and preserve module boundaries.

### 2. Implement dispatch buses
- [x] Add constructor-injected registries, private and immutable after construction.
- [x] Reject duplicate registrations and missing handlers; encapsulate generic casts.
- [x] Test request routing, typed results, and registration errors.
Acceptance: requests reach exactly their registered handler through typed bus APIs.

### 3. Introduce business handlers
- [x] Move retrieval and load/calculate/save orchestration into dedicated handlers.
- [x] Exercise handlers through buses, including empty and multi-deposit cases.
Acceptance: balances, metadata, withdrawals, and interest rules retain existing behavior.

### 4. Separate adapter mapping responsibilities
- [x] Extract TimeDepositApiMapper and joined JDBC result mapping.
- [x] Retain SQL, locking, batch updates, and explicit decimal conversions in persistence.
Acceptance: controllers delegate/map; repository adapters execute SQL; mappers translate.

### 5. Wire transactional command dispatch
- [x] Add bootstrap TransactionalCommandBus around complete delegated dispatch.
- [x] Wire handlers/buses explicitly; queries use the plain query dispatcher.
- [x] Verify transaction-before-load, rollback, concurrent credits, and query transaction behavior.
Acceptance: framework transactions stay outside the core and cover entire commands.

### 6. Switch REST and remove superseded code
- [x] Inject buses and API mapper; dispatch one request record per endpoint.
- [x] Remove superseded use-case interfaces, services, and balance-update decorator.
- [x] Migrate tests and references; retain the generated API and response statuses.
Acceptance: one command/query execution path per operation, preserving the HTTP contract.

### 7. Enforce architecture and document
- [x] Enforce core purity, inward dependencies, adapter isolation, and controller bus-port use.
- [x] Update README, IMPLEMENTATION_PLAN, and AI_WORKFLOW.
Acceptance: architecture checks and documentation reflect the implemented execution path.

### 8. Verify delivery
- [x] Run `mvn test` from `java/`.
- [x] Run `DOCKER_HOST=unix:///var/run/docker.sock mvn verify` from `java/`.
- [x] Run `git diff --check` and `git diff --exit-code -- java/src` from repository root.
- [x] Confirm exactly two contract operations and retain every Maven module.
- [x] Record actual results and blockers below.

## Delivery checkpoints

Introduce contracts/handlers alongside the existing path, then switch bootstrap and
controller together before removing superseded code. Keep each checkpoint buildable.
Create commits only when explicitly requested.

The user requested meaningful commits during implementation. Checkpoints:
1. Typed bus contracts, dispatchers, business handlers, and application tests.
2. Adapter mapping extraction, transactional dispatch wiring, and migrated verification.
3. Architecture and AI-workflow documentation with final verification results.

## Verification log

- Steps 1–3: `mvn -B -pl application -am verify` passed on JDK 27 with release 25:
  25 domain cases and 8 application/dispatch cases, no failures, errors, or skips.
- First checkpoint: `e58b715` — `refactor(application): introduce typed command and query dispatch`.
- Steps 4–6 and architecture enforcement: `mvn -B clean test` and
  `DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify` passed on JDK 27 with release 25.
  All 44 unit/controller/contract/architecture/legacy cases and 11 PostgreSQL integration
  cases passed without failures, errors, or skips. This includes rollback, transaction-before-load,
  concurrent updates, query dispatch without a transaction, and real-HTTP compatibility.
- Second checkpoint: `d87926b` — `refactor(java): route adapters through transactional command dispatch`.
- `git diff --check` and `git diff --exit-code -- java/src` passed. All original modules
  and exactly two contract operations are retained. No verification blockers remain.
- README, implementation tracking, agent guidance, and AI-workflow documentation now
  describe the command/query structure and execution paths. All eight steps are complete.
