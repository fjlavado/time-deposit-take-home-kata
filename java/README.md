# Time Deposit backend

Java 25 target, Maven multi-module build, Spring Boot 4.1.1, PostgreSQL, Spring Data JDBC, and Flyway.
The implementation follows the [assignment](../README.md), the tracked [implementation plan](IMPLEMENTATION_PLAN.md),
and the completed [Doodle-inspired refactoring plan](HEXAGONAL_REFACTOR_PLAN.md).

## Architecture

```text
                     bootstrap (Spring wiring + command transaction)
                        /                               \
            adapter-web                           adapter-persistence
                 |                                       |
          CommandBus / QueryBus ports                    |
                 |                                       |
          typed requests -> handlers                     |
                 +----------> domain <--------------------+
                            models, strategies,
                            outbound ports
```

| Module | Responsibility |
| --- | --- |
| `domain` | Single deposit model, calculator service, immutable plan/balance/withdrawal values, sealed interest strategies, repository port; no framework dependencies |
| `application` | Typed commands/queries, inbound bus ports, synchronous dispatchers, and constructor-injected handlers; depends only on domain |
| `adapter` | Maven parent/aggregator for all adapter modules |
| `adapter/adapter-persistence` | Spring Data JDBC table models/repositories, domain repository adapter and bidirectional mapper, aggregate persistence, Flyway SQL |
| `adapter/adapter-web` | OpenAPI-generated API interface and response DTOs, bus-delegating controller, dedicated API mapper, contract tests |
| `bootstrap` | Executable application, explicit adapter/handler/bus wiring, transactional command-bus decorator, architecture/end-to-end tests |

ArchUnit verifies framework-free core modules, inward dependencies, adapter isolation, controllers' use of bus ports rather than handlers/dispatch implementations, command/query handler separation, bootstrap ownership of transaction management, and confinement of strategy details to domain. The domain layer includes `org.ikigaidigital.domain..`, `org.ikigaidigital.interest..`, and `org.ikigaidigital.services..`. Maven separates compilation boundaries; only bootstrap produces an executable Boot jar.

### Command and query flow

This structure adopts Doodle's synchronous command/query bus pattern within the existing module boundaries.

- **POST**: `TimeDepositController` dispatches `UpdateTimeDepositBalancesCommand` through `CommandBus`. Its handler invokes `UpdateDepositBalancesUseCase`; bootstrap decorates `UpdateDepositBalancesService` with `@Transactional`. The service consumes an ID-only pending-deposit stream in groups of 100, loads each group's complete aggregates with inherited `CrudRepository.findAllById()`, credits unprocessed 30-day periods through the unchanged calculator, and persists each group through inherited `CrudRepository.saveAll()`. All groups share one transaction; the ID cursor closes before commit, which completes before the HTTP response. See [incremental update semantics](BATCH_BALANCE_UPDATES.md).
- **GET**: the controller dispatches `RetrieveTimeDepositsQuery(page, size)` through `QueryBus`. `DefaultQueryBus` selects `RetrieveTimeDepositsQueryHandler`, which requests a single page of deposits including withdrawals. The framework-free repository port uses domain `PageRequestVO` and `Page<TimeDeposit>` values; only the persistence adapter translates them to/from Spring Data paging. `TimeDepositApiMapper` converts deposits to generated response models, and the controller returns the generated page envelope. Query dispatch does not start a transaction. Reads use the same persisted model, so a subsequent GET sees a completed POST's updates.

The buses associate each request class with one handler in a private immutable registry. Duplicate registrations fail at construction; an unregistered request fails at dispatch. Request result types are carried by `Command<R>` and `Query<R>`; the update uses `Void` because it has no response payload. The runtime registry casts are confined to the dispatchers.

`UseCaseConfiguration` registers handlers and constructs both buses. `PersistenceAdapterConfiguration` explicitly enables Spring Data JDBC repository scanning and supplies the domain repository adapter and persistence mapper, while `WebAdapterConfiguration` supplies the controller and API mapper. Spring collects registered handlers for each bus. To extend application behavior, add a typed request and handler and register the handler in bootstrap; interest-rule extensions use `InterestStrategy`.

### Spring Data JDBC persistence

The persistence adapter follows Doodle's separation of annotated table models, Spring Data repository interfaces, and domain mapping:

- `jdbc/TimeDepositTable` is the aggregate root; its `Set<WithdrawalTable>` uses `@MappedCollection(idColumn = "timeDepositId")`. Both table models are immutable records with `@Table` and `@Id`; explicit camel-case mappings match the existing PostgreSQL schema. Monetary fields use `BigDecimal`; withdrawal dates use `LocalDate`. Empty mapped collections are normalized to an empty set.
- `repository/TimeDepositJdbcRepository` and `repository/TimeDepositBalanceJdbcRepository` extend `CrudRepository<TimeDepositTable, Integer>` and inherit aggregate operations. Withdrawals are loaded and saved through their owning deposit rather than through a separate repository.
- `repository/TimeDepositPageJdbcRepository` extends `PagingAndSortingRepository<TimeDepositIdTable, Integer>`. The ID-only record maps the same table and needs no additional schema. Its inherited `findAll(Pageable)` performs database pagination ordered by ID; the aggregate repository's inherited `findAllById(ids)` loads just that page's deposits and mapped withdrawals.
- Bootstrap enables [single-query loading](https://docs.spring.io/spring-data/relational/reference/jdbc/entity-persistence.html#jdbc.loading-aggregates) on `JdbcMappingContext` before initialization. Spring Data JDBC 4.1.1's direct paged aggregate read falls back to extra child queries. Paging IDs first keeps inherited repository operations while allowing `findAllById` to load the page's complete aggregates in one query. Each populated page uses at most three read queries: IDs, an optional total count, and aggregates. Empty pages skip aggregate loading; query count does not grow with deposits or withdrawals per page.
- `JdbcTimeDepositRepository` implements the read-only `TimeDepositRepository` port's `search(PageRequestVO)` and delegates aggregate conversion to `TimeDepositPersistenceMapper`. Loaded deposits and mapped withdrawals are sorted by ID in memory within the bounded page; selection and pagination happen in the database.
- `TimeDepositRepositoryAdapter` implements `TimeDepositRepositoryPort` for balance processing. `streamPending()` selects only IDs with `days > last_processed_days`, ordered by ID. The service buffers at most 100 IDs and calls `findAllById(ids)`; the adapter uses inherited Spring Data loading and maps complete aggregates, including withdrawals, in ID order. Only the current group is materialized. The service rechecks processing watermarks after aggregate loading to skip deposits processed since cursor creation.
- `saveAll()` maps complete domain snapshots back to table records, preserving withdrawal IDs/amounts/dates, plan, age, and loaded version, and calls inherited `CrudRepository.saveAll()`. `TimeDepositTable.version` uses `@Version`, so Spring Data JDBC performs optimistic version checking and increments. A stale or deleted row aborts the whole transaction. Aggregate saves may delete/reinsert mapped child rows; the mapper carries the full withdrawal collection so its persisted data is preserved.
- Bootstrap uses `spring-boot-starter-data-jdbc` and explicit `@EnableJdbcRepositories` because its application class is outside the persistence package. Its JDBC template sets fetch size 100; together with autocommit disabled by the outer transaction, this enables PostgreSQL server-side fetching of IDs. Bootstrap also enables single-query aggregate loading and owns the transaction boundary.

PostgreSQL integration tests instantiate actual Spring Data repository proxies and count prepared and plain JDBC read statements, including the total-count query. They cover bounded query counts, complete withdrawal collections, full/partial/out-of-range pages, and empty results. Their direct JDBC access is used for fixture setup and independent database assertions. Existing build, database configuration, and startup commands below also apply to this implementation; Flyway remains responsible for schema evolution.

## Build and test

Run commands from this `java/` directory. Prerequisites: Maven 3.6.3+ and JDK 25 or newer with support for release 25. JDK 27 is supported for compilation using `--release 25`; verification during development used JDK 27.

```sh
mvn test       # Unit, controller/contract, architecture, and shared strategy tests; no Docker required
mvn verify     # Also runs PostgreSQL Testcontainers integration tests and packages the application
```

`*Test` classes run with Surefire; `*IT` classes run with Failsafe. Integration tests require a reachable Docker daemon and pull PostgreSQL `17.6-alpine`. They use isolated databases; no manually started database is required. Tests fail rather than silently skip when Docker is unavailable.

### Current refactor verification status

The latest aggregate-persistence and schedule-value-object refactor passed on JDK 27
with release 25: `mvn test` ran **162 non-integration cases**, and
`DOCKER_HOST=unix:///var/run/docker.sock mvn verify` passed those plus
**29 PostgreSQL integration cases**, with no failures, errors, or skips. Coverage
includes the real ID-only cursor, bounded aggregate loading/saving, withdrawal preservation,
cursor closure on failures, framework-managed optimistic locking, multi-group rollback,
concurrent HTTP updates, and schedule boundaries.

If an inherited `DOCKER_HOST` targets an unavailable remote daemon, select a working endpoint for the command. On a Linux host with the local Docker socket:

```sh
DOCKER_HOST=unix:///var/run/docker.sock mvn verify
```

## Run locally

```sh
docker compose up -d --wait postgres
mvn package
java -jar bootstrap/target/bootstrap-1.0-SNAPSHOT.jar
```

Startup applies and validates Flyway migrations. A fresh database starts empty.

| Environment variable | Local default |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/time_deposits` |
| `DB_USERNAME` | `time_deposit` |
| `DB_PASSWORD` | `time_deposit` |
| `PORT` | `8080` |

The Compose credentials are local development defaults. The application also supports externally supplied database settings.

For example data, after the first successful application startup:

```sh
docker compose exec -T postgres psql -U time_deposit -d time_deposits < docs/sample-data.sql
```

The sample script inserts additional rows each time it is run. It is an explicit demonstration script, not a startup initializer or migration.

Stop the local database with `docker compose down`; its named volume retains data.

## API

| Method | Path | Result |
| --- | --- | --- |
| GET | `/time-deposits?page=0&size=20` | 200 with one ID-ordered page, including withdrawals and pagination metadata |
| POST | `/time-deposits/balance-updates` | 204, empty body, after crediting and persisting interest |

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

The OpenAPI contract is version **3.0.0** because POST now processes pending periods idempotently rather than crediting interest on every invocation. The paginated GET shape introduced in version 2 remains the same.

POST returns **204** after processing pending periods, and another call at the same deposit ages is a no-op. Overlapping requests that read stale versions receive **409 Conflict**, with their entire transaction rolled back; retrying reloads current state. Days and withdrawals remain unchanged. The detailed schedule, migration assumptions, and concurrency behavior are documented in [BATCH_BALANCE_UPDATES.md](BATCH_BALANCE_UPDATES.md).

### Contract-first code generation

[`docs/openapi.yaml`](docs/openapi.yaml) is the source of truth for HTTP mappings, response statuses, and response models. The web adapter runs the pinned OpenAPI Generator Maven plugin (7.26.0, Spring generator with Spring Boot 4/Jackson 3 support) automatically during `generate-sources`, including when running `mvn test`, `mvn verify`, or `mvn package`.

To generate the contract code on its own:

```sh
mvn -pl adapter/adapter-web -am generate-sources
```

Generated `TimeDepositsApi`, `TimeDepositPageResponse`, `TimeDepositResponse`, and `WithdrawalResponse` sources live under `adapter/adapter-web/target/generated-sources/openapi/src/main/java/`, in `org.ikigaidigital.infrastructure.web.generated` subpackages. Maven adds that directory to compilation automatically. Generated files are build output: edit the contract and regenerate rather than editing or committing them.

`TimeDepositController` implements the generated interface, dispatches typed command/query records, and delegates domain-to-response conversion to `TimeDepositApiMapper`. The generator emits abstract operation signatures so contract changes must be implemented at compile time. The contract's `double` balance, decimal withdrawal amount (`BigDecimal`), and date (`LocalDate`) mappings preserve the existing deposit representation. The web adapter includes the validation starter required by the generated pagination parameter constraints.

### Swagger contract

The [OpenAPI contract](docs/openapi.yaml) describes exactly the two business operations. It is not served through an additional application endpoint.

To use Swagger UI, keep the application running and launch a separate UI from this directory:

```sh
docker run --rm -p 8081:8080 \
  -e SWAGGER_JSON=/spec/openapi.yaml \
  -v "$PWD/docs/openapi.yaml:/spec/openapi.yaml:ro" \
  swaggerapi/swagger-ui:v5.17.14
```

Open <http://localhost:8081>, expand either operation, select **Try it out**, then **Execute**. The contract targets `http://localhost:8080`; the backend allows the UI's `http://localhost:8081` origin for these two operations. Adjust the contract's server URL if the application's port changes.

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

The update service passes each historical-period deposit to the calculator and saves
the complete updated aggregates within the same transaction. The no-argument calculator retains the three
built-in rules and `updateBalance(List<TimeDeposit>)` mutates balances in place.
`Double getBalance()` and `setBalance(Double)` retain their balance API. The deposit
encapsulates immutable `Balance` values and credits already-rounded interest via
`deposit.updateBalance(interest)`; `Balance.addInterest` returns a new value.
Metadata and withdrawals are preserved. The 60 calculator cases now reflect the current
rounding behavior; additional cases cover permitted strategies, typed registration and
resolution, plan/balance values, deposit updates, and withdrawal immutability. See
[CALCULATOR_REFACTOR_PLAN.md](CALCULATOR_REFACTOR_PLAN.md) for the design and results.

`InterestPeriodSchedule`, an immutable value object containing `PlanType`, selects
the pending 30-day credit dates from a deposit's age and processing watermark.
`TimeDeposit.pendingInterestDays()` delegates to it. The application presents each
selected period to the calculator in chronological order; the schedule does not
calculate or round monetary amounts.

## Flyway schema evolution

Migrations live in `adapter/adapter-persistence/src/main/resources/db/migration/` and are loaded from that dependency's classpath by bootstrap.

- `V1__create_time_deposit_schema.sql` creates the quoted `"timeDeposits"` table and camel-case columns, `withdrawals`, required constraints, and the foreign-key index.
- `V2__add_withdrawals_and_calculation_tracking.sql` adds non-null `last_processed_days` and `version`, both defaulting to zero. Withdrawals and its index already exist in V1. Existing balances are treated as opening principal; see the migration assumptions in the batch documentation.
- Do not modify an already-applied migration; startup validates recorded checksums.
- Flyway is the only schema initializer. Spring SQL initialization is disabled; no ORM schema generation is used.
- Flyway clean is disabled in application configuration. Integration tests use disposable PostgreSQL instances.

## AI-assisted workflow

See [AI_WORKFLOW.md](AI_WORKFLOW.md) for the actual harness, model, instructions, setup, and assisted work. Original implementation progress is tracked in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md); the command/query refactor checkpoints and verification results are tracked in [HEXAGONAL_REFACTOR_PLAN.md](HEXAGONAL_REFACTOR_PLAN.md).
