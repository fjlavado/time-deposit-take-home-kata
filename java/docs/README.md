# Time Deposit backend

Java 25 target, Maven multi-module build, Spring Boot 4.1.1, PostgreSQL, Spring Data JDBC, and Flyway.
The implementation follows the [assignment](../../README.md), the tracked [implementation plan](IMPLEMENTATION_PLAN.md),
and the completed [Doodle-inspired refactoring plan](HEXAGONAL_REFACTOR_PLAN.md).

This is the Java documentation entry point. All commands and source paths in these
guides are relative to `java/`, unless stated otherwise; run commands from that
directory, not `java/docs/`. Agent instructions remain in [AGENTS.md](../AGENTS.md).
See [architecture ownership](ARCHITECTURE.md) for the enforced module boundaries.

## Architecture

```text
                    bootstrap (composition and lifecycle)
                   /                |                  \
           adapter-web       adapter-runtime      adapter-persistence
                   \                |                  /
                    application (inbound/outbound ports,
                     handlers, jobs and pagination)
                                    |
                             domain (models,
                         calculator and strategies)
```

| Module | Responsibility |
| --- | --- |
| `domain` | Single deposit model, calculator service, immutable plan/balance/withdrawal values and sealed interest strategies; no framework dependencies |
| `application` | Typed commands/queries, inbound bus ports, outbound repository ports, pagination contracts, job execution state, synchronous dispatchers and handlers; depends only on domain |
| `adapter` | Maven parent/aggregator for all adapter modules |
| `adapter/adapter-persistence` | Spring Data JDBC table models/repositories, application repository-port adapters, bidirectional mapper, independent audit transactions, aggregate persistence and Flyway SQL |
| `adapter/adapter-web` | OpenAPI-generated API interface and response DTOs, bus-delegating controller, API mapper, CORS policy and contract tests |
| `adapter/adapter-runtime` | Executor-backed task submission and transactional use-case/command-bus decorators; depends only inward on application |
| `bootstrap` | Executable application, explicit component wiring, managed-resource lifecycle and architecture/end-to-end tests |

ArchUnit verifies framework-free core modules, inward dependencies, adapter isolation, controller bus-port use, command/query handler separation, infrastructure-owned transaction management, configuration-only bootstrap classes, and confinement of `services.interest` strategy details to domain. The domain layer includes `org.ikigaidigital.domain..` and `org.ikigaidigital.services..`. Maven separates compilation boundaries; only bootstrap produces an executable Boot jar. The calculator is a plain domain object constructed when composing the update service, not a Spring bean.

### Command and query flow

This structure adopts Doodle's synchronous command/query bus pattern within the existing module boundaries.

- **POST**: `TimeDepositController` dispatches `StartBalanceUpdateJobCommand` through `CommandBus`. Its handler invokes the `BalanceUpdateJob` port. Application's framework-free `BackgroundBalanceUpdateJob` reserves a UUID through the execution-repository port, then submits work through `BackgroundTaskExecutor`. The persistence decorator commits reservation before returning, and the runtime adapter submits to the managed executor. POST returns **202 STARTED**, or **409 RUNNING** with the active UUID. The worker invokes `UpdateDepositBalancesUseCase`, decorated by the runtime adapter's `@Transactional` implementation. The service consumes all deposit IDs in groups of 100, loads complete aggregates through inherited `CrudRepository.findAllById()`, applies the unchanged calculator once, and persists through inherited `CrudRepository.saveAll()`. All groups share one worker-thread transaction; the ID cursor closes before commit. See [background jobs](BACKGROUND_BALANCE_UPDATES.md) and [bulk update semantics](BATCH_BALANCE_UPDATES.md).
- **GET**: the controller dispatches `RetrieveTimeDepositsQuery(page, size)` through `QueryBus`. `DefaultQueryBus` selects `RetrieveTimeDepositsQueryHandler`, which requests a single page including withdrawals. The application-owned repository port uses `application.pagination.PageRequestVO` and `Page<TimeDeposit>`; only persistence translates Spring Data paging. `TimeDepositApiMapper` converts deposits to generated response models. Query dispatch does not start a transaction. GET sees committed balances; immediately after acceptance it may still show previous balances.

The buses associate each request class with one handler in a private immutable registry. Duplicate registrations fail at construction; an unregistered request fails at dispatch. Request result types are carried by `Command<R>` and `Query<R>`. Job submission returns a framework-free `BalanceUpdateJobResult`; the internal synchronous `UpdateTimeDepositBalancesCommand` retains `Void` for direct processing callers. The runtime registry casts are confined to the dispatchers. The transactional bus bypasses its outer transaction for job submission, which owns short audit transactions instead; this avoids holding a request-thread connection during admission.

`UseCaseConfiguration` registers handlers and composes the services, decorators and buses. `BalanceUpdateJobConfiguration` wires the application job, runtime executor adapter and managed single-thread executor, whose shutdown drains work before the datasource closes. `PersistenceAdapterConfiguration` enables JDBC repository scanning and wires persistence adapters, including `TransactionalBalanceUpdateExecutionRepository`. `WebAdapterConfiguration` imports the controller and web-owned `SwaggerCorsConfiguration` and supplies the API mapper. Spring collects registered handlers for each bus. Interest-rule extensions use `InterestStrategy`.

### Spring Data JDBC persistence

The persistence adapter follows Doodle's separation of annotated table models, Spring Data repository interfaces, and domain mapping:

- `jdbc/TimeDepositTable` is the aggregate root; its `Set<WithdrawalTable>` uses `@MappedCollection(idColumn = "timeDepositId")`. Both table models are immutable records with `@Table` and `@Id`; explicit camel-case mappings match the existing PostgreSQL schema. Monetary fields use `BigDecimal`; withdrawal dates use `LocalDate`. Empty mapped collections are normalized to an empty set.
- `repository/TimeDepositJdbcRepository` and `repository/TimeDepositBalanceJdbcRepository` extend `CrudRepository<TimeDepositTable, Integer>` and inherit aggregate operations. Withdrawals are loaded and saved through their owning deposit rather than through a separate repository.
- `repository/TimeDepositPageJdbcRepository` extends `PagingAndSortingRepository<TimeDepositIdTable, Integer>`. The ID-only record maps the same table and needs no additional schema. Its inherited `findAll(Pageable)` performs database pagination ordered by ID; the aggregate repository's inherited `findAllById(ids)` loads just that page's deposits and mapped withdrawals.
- Bootstrap enables [single-query loading](https://docs.spring.io/spring-data/relational/reference/jdbc/entity-persistence.html#jdbc.loading-aggregates) on `JdbcMappingContext` before initialization. Spring Data JDBC 4.1.1's direct paged aggregate read falls back to extra child queries. Paging IDs first keeps inherited repository operations while allowing `findAllById` to load the page's complete aggregates in one query. Each populated page uses at most three read queries: IDs, an optional total count, and aggregates. Empty pages skip aggregate loading; query count does not grow with deposits or withdrawals per page.
- `JdbcTimeDepositRepository` implements the read-only `TimeDepositRepository` port's `search(PageRequestVO)` and delegates aggregate conversion to `TimeDepositPersistenceMapper`. Loaded deposits and mapped withdrawals are sorted by ID in memory within the bounded page; selection and pagination happen in the database.
- `TimeDepositRepositoryAdapter` implements `TimeDepositRepositoryPort` for balance processing. `streamAllIds()` selects every deposit ID, ordered by ID. The service buffers at most 100 IDs and calls `findAllById(ids)`; the adapter uses inherited Spring Data loading and maps complete aggregates, including withdrawals, in ID order. Only the current group is materialized.
- `saveAll()` maps complete domain snapshots back to table records, preserving withdrawal IDs/amounts/dates, plan, age, and loaded version, and calls inherited `CrudRepository.saveAll()`. `TimeDepositTable.version` uses `@Version`, so Spring Data JDBC performs optimistic version checking and increments. A stale or deleted row aborts the whole transaction. Aggregate saves may delete/reinsert mapped child rows; the mapper carries the full withdrawal collection so its persisted data is preserved.
- Bootstrap uses `spring-boot-starter-data-jdbc` and explicit `@EnableJdbcRepositories` because its application class is outside the persistence package. Its JDBC template sets fetch size 100; together with autocommit disabled by the runtime decorator's transaction, this enables PostgreSQL server-side fetching of IDs. Bootstrap configures single-query loading and wires transaction decorators; the decorators live in adapters.

PostgreSQL integration tests instantiate actual Spring Data repository proxies and count prepared and plain JDBC read statements, including the total-count query. They cover bounded query counts, complete withdrawal collections, full/partial/out-of-range pages, and empty results. Their direct JDBC access is used for fixture setup and independent database assertions. Existing build, database configuration, and startup commands below also apply to this implementation; Flyway remains responsible for schema evolution.

## Build and test

Run commands from `java/`. Prerequisites: Maven 3.6.3+ and JDK 25 or newer with support for release 25. JDK 27 is supported for compilation using `--release 25`; verification during development used JDK 27.

```sh
mvn test       # Unit, controller/contract, architecture, and shared strategy tests; no Docker required
mvn verify     # Also runs PostgreSQL Testcontainers integration tests and packages the application
```

`*Test` classes run with Surefire; `*IT` classes run with Failsafe. Integration tests require a reachable Docker daemon and pull PostgreSQL `17.6-alpine`. They use isolated databases; no manually started database is required. Tests fail rather than silently skip when Docker is unavailable.

### Current refactor verification status

The strict hexagonal ownership refactor passed `mvn -q clean test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn -q -Pperformance verify` on JDK 27
with release 25. All nine architecture checks and 36 PostgreSQL integration cases
passed without failures, errors or skips. The optional performance module builds;
the measured stress results below remain historical, not a new benchmark run.

Verification uses JDK 27 with release 25. The suite covers per-invocation calculation,
eligibility boundaries, repeated credits at unchanged ages, the real ID-only cursor,
bounded aggregate loading/saving, withdrawal preservation, cursor closure on failures,
framework-managed optimistic locking, multi-group rollback, and concurrent HTTP updates.
Historical verification results are recorded in `IMPLEMENTATION_PLAN.md`.
The incremental-processing removal and schema cleanup passed `mvn -q clean test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn -q verify`: **158 non-integration cases
and 29 PostgreSQL integration cases**, without failures, errors, or skips.

The background-job checkpoint passed `mvn -q clean test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn -q verify`: **160 non-integration cases
and 35 PostgreSQL integration cases**, without failures, errors, or skips. It adds
database-wide admission/UUID audit coverage, asynchronous 202/409 contract checks,
worker-thread rollback and retry, and submission transaction-boundary checks.

If an inherited `DOCKER_HOST` targets an unavailable remote daemon, select a working endpoint for the command. On a Linux host with the local Docker socket:

```sh
DOCKER_HOST=unix:///var/run/docker.sock mvn verify
```

## Run with Docker Compose

From `java/`, with Docker Engine and Compose v2 available:

```sh
docker compose up --build -d --wait
```

This builds the **`time-deposit-kata:dev`** image, waits for PostgreSQL to be
healthy, starts the app with the **`dev`** profile, and waits until its HTTP/database
health check passes. On a fresh volume the sample data is ready immediately.
Open **<http://localhost:8080/swagger-ui.html>**, expand an operation, select
**Try it out**, then **Execute**. The raw contract is at
<http://localhost:8080/openapi.yaml>.

```sh
curl 'http://localhost:8080/time-deposits?page=0&size=20'
curl -i -X POST http://localhost:8080/time-deposits/balance-updates
docker compose logs -f app
```

POST acknowledges a background execution; inspect its terminal status in the
database, then use GET to see the updated balances:

```sh
docker compose exec -T postgres psql -U time_deposit -d time_deposits \
  -c 'SELECT id, status, started_at, finished_at FROM balance_update_executions ORDER BY started_at DESC;'
```

The multi-stage [Dockerfile](../Dockerfile) compiles the reactor with Maven 3.9 /
Temurin JDK 25 and packages just the executable Boot jar into a Temurin Java 25
JRE image. The runtime uses a non-root user; the profile is selected at runtime,
not baked into the image. A BuildKit Maven cache accelerates rebuilds. The image
build skips test execution; run `mvn verify` separately with a reachable Docker
daemon for the Testcontainers suite. Build the image on its own with:

```sh
docker build -t time-deposit-kata:dev .
```

### Ports, profiles and persisted data

Compose defaults to host ports `8080` for HTTP and `5432` for PostgreSQL. Override
them when occupied (the internal application/database ports remain unchanged):

```sh
PORT=18080 POSTGRES_PORT=55432 docker compose up --build -d --wait
```

Then open <http://localhost:18080/swagger-ui.html>. To run without automatic
sample seeding, set `SPRING_PROFILES_ACTIVE=default` when bringing up Compose.
Existing data is retained when changing profiles.

```sh
docker compose down          # Stop app/database; retain the named database volume
docker compose down -v       # Delete this stack's database data for a fresh sample run
docker compose up -d --wait  # Recreate the database and seed with dev
```

Use the same port/profile overrides on subsequent Compose commands. If running
multiple independent stacks, use `docker compose -p <name>` with distinct host
ports so their containers, networks and database volumes stay isolated.

## Run locally with a host JVM

```sh
docker compose up -d --wait postgres
mvn package
java -jar bootstrap/target/bootstrap-1.0-SNAPSHOT.jar
```

Startup applies and validates Flyway migrations. With the default profile a fresh
database starts empty. For a ready-to-explore sample dataset, enable `dev`:

```sh
java -jar bootstrap/target/bootstrap-1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

Alternatively set `SPRING_PROFILES_ACTIVE=dev`. Open
<http://localhost:8080/swagger-ui.html> to retrieve sample deposits and start a
balance update.

| Environment variable | Local default |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/time_deposits` |
| `DB_USERNAME` | `time_deposit` |
| `DB_PASSWORD` | `time_deposit` |
| `PORT` | `8080` |
| `SPRING_PROFILES_ACTIVE` | No active profile; use `dev` to seed an empty database |

The Compose credentials are local development defaults. The application also supports externally supplied database settings.

### Development sample data

The `dev` profile adds a Flyway `afterMigrate` callback from the persistence
adapter's `db/dev/` location. It inserts **120 deposits and 120 withdrawals** only
when the deposit table is empty. Existing databases are left intact; restarts
preserve credited balances and do not duplicate samples. Deleting every deposit
causes the next dev startup to seed again. The callback is transactional and
serializes concurrent seed attempts. It adds no versioned migration, so switching
back to the default profile keeps Flyway validation compatible.

On a fresh database, the first twelve deposits are:

| ID | Plan | Days | Initial balance | Balance after one completed update |
| --- | --- | --- | --- | --- |
| 1 | basic | 30 | 1000 | 1000 |
| 2 | basic | 31 | 1000 | 1000.83 |
| 3 | student | 30 | 1000 | 1000 |
| 4 | student | 31 | 1000 | 1002.50 |
| 5 | student | 365 | 1000 | 1002.50 |
| 6 | student | 366 | 1000 | 1000 |
| 7 | premium | 45 | 1000 | 1000 |
| 8 | premium | 46 | 1000 | 1004.17 |
| 9 | basic | 31 | 18 | 18.02 |
| 10 | student | 31 | 6 | 6.02 |
| 11 | FuturePlan | 46 | 1000 | 1000 |
| 12 | basic | 31 | 0 | 0 |

The other 108 deposits cycle through the three supported plans at day 60, with
balances from 1000.10 through 1010.80. There are six pages at the default size 20
and two processing groups at size 100. Even-ID deposits have two withdrawals
(`12.34` on `2026-01-02` and `0.10` on `2026-01-03`); odd-ID deposits have none.
IDs are generated normally; a previously used identity sequence can start higher.
POST returns acceptance immediately; use GET after completion to observe the
changes. Repeat successful POSTs apply interest again.

For a small manual alternative without the dev profile, after startup:

```sh
docker compose exec -T postgres psql -U time_deposit -d time_deposits < docs/sample-data.sql
```

The sample script inserts additional rows each time it is run. It is an explicit demonstration script, not a startup initializer or migration.

For a repeatable large dataset and stress testing, see
[performance testing](performance/README.md). Its isolated database supports
100,000 deposits and approximately 500,000 withdrawals by default.

Stop the local database with `docker compose down`; its named volume retains data.

## API

| Method | Path | Result |
| --- | --- | --- |
| GET | `/time-deposits?page=0&size=20` | 200 with one ID-ordered page, including withdrawals and pagination metadata |
| POST | `/time-deposits/balance-updates` | 202 with `STARTED` and a new UUID, or 409 with `RUNNING` and the active UUID |

```sh
curl 'http://localhost:8080/time-deposits?page=0&size=20'
curl -i -X POST http://localhost:8080/time-deposits/balance-updates
curl http://localhost:8080/time-deposits
```

Example GET response:

```json
{
  "content": [{
    "id": 1,
    "planType": "basic",
    "balance": 1000.83,
    "days": 31,
    "withdrawals": [{"id": 1, "amount": 12.34, "date": "2026-01-02"}]
  }],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

`page` is zero-based and defaults to `0`; `size` is positive and defaults to `20`. Request subsequent pages until `page + 1` reaches `totalPages` to retrieve the complete collection. A page beyond the last returns `content: []` while retaining totals; an empty database has zero elements and zero pages. Deposits without withdrawals have `withdrawals: []`. Withdrawal fields are an explicit interpretation of the assignment's unspecified nested response shape.

The OpenAPI contract is version **5.0.0** for asynchronous POST responses. The paginated GET shape introduced in version 2 remains the same.

Example **202 Accepted** response:

```json
{
  "jobId": "a39bbdbe-5aa4-4ccc-99c9-08f0f67b4b1d",
  "status": "STARTED",
  "message": "Balance update job accepted."
}
```

While that execution is queued or running, another POST returns **409 Conflict** with
the same `jobId`, `status: "RUNNING"`, and `message: "A balance update job is already running."`.
The database's partial unique index prevents overlapping jobs even across application
instances. Busy requests create no extra execution rows. After completion or failure,
another POST starts a new execution; each completed execution may credit eligible
deposits again at their current ages. POST is not a completion-polling operation.
Days and withdrawals remain unchanged. The execution log records `STARTED`, `RUNNING`,
`COMPLETED`, or `FAILED`, with start/finish timestamps. Failures roll back the balance
transaction before the failed audit is recorded.

**There is no execution-log query endpoint:** the assignment requires exactly two
business endpoints, and a public execution-history/status API would expand the kata's
scope. The table is an internal audit and concurrency gate. Inspect counts/outcomes
using the SQL examples in [BACKGROUND_BALANCE_UPDATES.md](BACKGROUND_BALANCE_UPDATES.md).
Calculation behavior is documented in [BATCH_BALANCE_UPDATES.md](BATCH_BALANCE_UPDATES.md).

### Contract-first code generation

[`docs/openapi.yaml`](openapi.yaml) is the source of truth for HTTP mappings, response statuses, and response models. The web adapter runs the pinned OpenAPI Generator Maven plugin (7.26.0, Spring generator with Spring Boot 4/Jackson 3 support) automatically during `generate-sources`, including when running `mvn test`, `mvn verify`, or `mvn package`.

To generate the contract code on its own:

```sh
mvn -pl adapter/adapter-web -am generate-sources
```

Generated `TimeDepositsApi`, `TimeDepositPageResponse`, `TimeDepositResponse`, `WithdrawalResponse`, and `BalanceUpdateJobResponse` sources live under `adapter/adapter-web/target/generated-sources/openapi/src/main/java/`, in `org.ikigaidigital.infrastructure.web.generated` subpackages. Maven adds that directory to compilation automatically. Generated files are build output: edit the contract and regenerate rather than editing or committing them. Generated operation signatures use `ResponseEntity` so the controller can choose POST's 202 or 409 at runtime.

`TimeDepositController` implements the generated interface, dispatches typed command/query records, and delegates domain-to-response conversion to `TimeDepositApiMapper`. The generator emits abstract operation signatures so contract changes must be implemented at compile time. The contract's `double` balance, decimal withdrawal amount (`BigDecimal`), and date (`LocalDate`) mappings preserve the existing deposit representation. The web adapter includes the validation starter required by the generated pagination parameter constraints.

### Swagger contract

The [OpenAPI contract](openapi.yaml) describes exactly the two business operations.
The web adapter packages that same source file at `/openapi.yaml` and serves an
embedded console at **<http://localhost:8080/swagger-ui.html>**. Open it with the
application running, expand either operation, select **Try it out**, then **Execute**.

Swagger UI 5.17.14 is pinned as a WebJar dependency; its JavaScript and CSS are
served by the application without a CDN or a separate UI container. The relative
OpenAPI server URL follows the application's host, port and context path, so
changing `PORT` also works for interactive requests. Documentation resources do
not add business operations. The existing CORS allowance for an optional external
UI at `http://localhost:8081` remains available; such a UI must target the backend's
`/openapi.yaml` URL.

## Interest rules and current domain API

Eligibility and rates retain the starter's division by twelve:

| Exact plan name | Eligible days | Interest per invocation |
| --- | --- | --- |
| `basic` | `days > 30` | `balance * 0.01 / 12` |
| `student` | `days > 30 && days < 366` | `balance * 0.03 / 12` |
| `premium` | `days > 45` | `balance * 0.05 / 12` |

Rates are interpreted as annual percentages credited monthly. Matching is case-sensitive; unknown plan strings are returned unchanged and earn no interest.

Calculation uses double arithmetic followed by `BigDecimal.valueOf(interest)` and
`setScale(2, RoundingMode.HALF_UP)`. This differs from the earlier `new BigDecimal(interest)`
conversion: basic balance 18 now becomes **18.02** (previously 18.01), and student balance
6 becomes **6.02** (previously 6.01). Tests explicitly cover these changed half-cent results.
**The final balance is not separately rounded.** Two basic credits on 1000 still produce
`1001.6600000000001`. PostgreSQL uses unconstrained `NUMERIC`; the persistence mapper reads
balances with `BigDecimal.doubleValue()`, and writes use `BigDecimal.valueOf(deposit.getBalance())`.
Withdrawal amounts remain `BigDecimal` throughout.

Sources and tests live in `domain/src/`; the separate `legacy` module has been removed.
The single deposit class is now `org.ikigaidigital.domain.interest.TimeDeposit`, and the
calculator is `org.ikigaidigital.services.TimeDepositCalculator`. `Balance`, `PlanType`, and
the generic `ValueObject<T>` interface live in `org.ikigaidigital.domain.interest.vo`.
Value objects expose `value()`. `PlanType` rejects null names and preserves the exact
string, including unknown names, capitalization, and whitespace; record equality enables
lookup using independently constructed equal values. Constants are `BASIC`, `STUDENT`,
`PREMIUM`, and `UNKNOWN` (the empty name).

Both deposit constructors now accept `PlanType`, and `getPlanType()` returns it. The
four-argument constructor defaults to no withdrawals; the five-argument constructor
defensively copies a read-only withdrawals list. These package and plan-type changes
require callers to migrate imports and string arguments. HTTP and database plan fields
remain strings; the web mapper unwraps `planType.value()`, and the persistence mapper
wraps stored names in `PlanType` when constructing domain deposits.

`InterestStrategy` is sealed, permitting only `BasicInterestStrategy`,
`StudentInterestStrategy`, `PremiumInterestStrategy`, and `NoInterestStrategy`. Each
declares its `planType()`. The default resolver builds its registry from those declarations
for the three interest-bearing plans. It returns `NoInterestStrategy` when a plan is
unregistered or the registered strategy's `apply(deposit)` returns the no-interest
singleton. Each interest-bearing strategy returns its singleton when eligible;
the default `apply` implementation returns the no-interest singleton.

The resolver's and calculator's map constructors accept a complete `Map<PlanType, InterestStrategy>`
registry, which the resolver defensively copies. Existing permitted strategies can be registered
under aliases or used to override a plan. Arbitrary lambdas and anonymous implementations
are no longer supported; adding a new strategy requires updating the sealed permits list.
For example, this resolver uses the premium rule for an alias without adding built-in plans:

```java
import java.util.Map;
import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;
import org.ikigaidigital.services.interest.InterestStrategyResolver;
import org.ikigaidigital.services.interest.PremiumInterestStrategy;

var loyalty = new PlanType("loyalty");
var resolver = new InterestStrategyResolver(Map.of(loyalty, PremiumInterestStrategy.PREMIUM_INTEREST_STRATEGY));
var deposit = new TimeDeposit(1, loyalty, 1000.0, 46);
double interest = resolver.resolve(deposit).calculateInterestRounded(deposit); // 4.17
```

The update service passes each loaded group directly to the calculator once and saves
the complete updated aggregates within the same transaction. The no-argument calculator retains the three
built-in rules and `updateBalance(List<TimeDeposit>)` mutates balances in place.
`Double getBalance()` and `setBalance(Double)` retain their balance API. The deposit
encapsulates immutable `Balance` values and credits already-rounded interest via
`deposit.updateBalance(interest)`; `Balance.addInterest` returns a new value.
Metadata and withdrawals are preserved. The 60 calculator cases now reflect the current
rounding behavior; additional cases cover permitted strategies, typed registration and
resolution, plan/balance values, deposit updates, and withdrawal immutability. See
[CALCULATOR_REFACTOR_PLAN.md](CALCULATOR_REFACTOR_PLAN.md) for the design and results.

Eligibility is evaluated at each deposit's current age. The application does not
construct historical-period deposits or infer missing monthly credits.

## Flyway schema evolution

Migrations live in `adapter/adapter-persistence/src/main/resources/db/migration/` and are loaded from that dependency's classpath by bootstrap.

- `V1__create_time_deposit_schema.sql` creates the quoted `"timeDeposits"` table and camel-case columns, `withdrawals`, required constraints, and the foreign-key index.
- `V2__add_withdrawals_and_calculation_tracking.sql` historically added `last_processed_days` and `version`, both defaulting to zero. The application no longer maps or uses `last_processed_days`; `version` still supports optimistic locking. Withdrawals and its index already exist in V1.
- `V3__remove_calculation_watermark.sql` drops `last_processed_days` and its check constraint. Existing balances, versions, withdrawals, and their index are preserved; no previously credited interest is reversed. Integration coverage upgrades populated V1 and V2 schemas and validates their migration histories.
- `V4__create_balance_update_executions.sql` adds the UUID execution audit and a partial unique index permitting only one `STARTED`/`RUNNING` row database-wide. Terminal-status/timestamp constraints keep the minimal log consistent. No existing deposit data changes.
- The optional `db/dev/afterMigrate__sample_data.sql` callback seeds only empty databases with the `dev` profile. It is not a schema migration and is not included in default-profile startup.
- Do not modify an already-applied migration; startup validates recorded checksums.
- Flyway is the only schema initializer. Spring SQL initialization is disabled; no ORM schema generation is used.
- Flyway clean is disabled in application configuration. Integration tests use disposable PostgreSQL instances.

## AI-assisted workflow

See [AI_WORKFLOW.md](AI_WORKFLOW.md) for the actual harness, model, instructions, setup, and assisted work. Original implementation progress is tracked in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md); the command/query refactor checkpoints and verification results are tracked in [HEXAGONAL_REFACTOR_PLAN.md](HEXAGONAL_REFACTOR_PLAN.md).
