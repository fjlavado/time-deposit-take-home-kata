# Doodle-Inspired Hexagonal Refactoring Plan

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
- [ ] Extract TimeDepositApiMapper and joined JDBC result mapping.
- [ ] Retain SQL, locking, batch updates, and explicit decimal conversions in persistence.
Acceptance: controllers delegate/map; repository adapters execute SQL; mappers translate.

### 5. Wire transactional command dispatch
- [ ] Add bootstrap TransactionalCommandBus around complete delegated dispatch.
- [ ] Wire handlers/buses explicitly; queries use the plain query dispatcher.
- [ ] Verify transaction-before-load, rollback, concurrent credits, and query transaction behavior.
Acceptance: framework transactions stay outside the core and cover entire commands.

### 6. Switch REST and remove superseded code
- [ ] Inject buses and API mapper; dispatch one request record per endpoint.
- [ ] Remove superseded use-case interfaces, services, and balance-update decorator.
- [ ] Migrate tests and references; retain the generated API and response statuses.
Acceptance: one command/query execution path per operation, preserving the HTTP contract.

### 7. Enforce architecture and document
- [ ] Enforce core purity, inward dependencies, adapter isolation, and controller bus-port use.
- [ ] Update README, IMPLEMENTATION_PLAN, and AI_WORKFLOW.
Acceptance: architecture checks and documentation reflect the implemented execution path.

### 8. Verify delivery
- [ ] Run `mvn test` from `java/`.
- [ ] Run `DOCKER_HOST=unix:///var/run/docker.sock mvn verify` from `java/`.
- [ ] Run `git diff --check` and `git diff --exit-code -- java/src` from repository root.
- [ ] Confirm exactly two contract operations and retain every Maven module.
- [ ] Record actual results and blockers below.

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
- Remaining steps are in progress.
