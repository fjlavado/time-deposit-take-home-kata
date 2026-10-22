# Time Deposit backend

Java 25 target, Maven multi-module build, Spring Boot 4.1.1, PostgreSQL, Spring JDBC, and Flyway.
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
                            models, rules,
                            outbound ports

legacy: isolated starter code, not used by the new backend
```

| Module | Responsibility |
| --- | --- |
| `domain` | Immutable models, extensible interest policies, repository port; no framework dependencies |
| `application` | Typed commands/queries, inbound bus ports, synchronous dispatchers, and constructor-injected handlers; depends only on domain |
| `adapter` | Maven parent/aggregator for all adapter modules |
| `adapter/adapter-persistence` | JDBC repository adapter, dedicated joined-row mapper, batched balance persistence, Flyway SQL |
| `adapter/adapter-web` | OpenAPI-generated API interface and response DTOs, bus-delegating controller, dedicated API mapper, contract tests |
| `bootstrap` | Executable application, explicit adapter/handler/bus wiring, transactional command-bus decorator, architecture/end-to-end tests |
| `legacy` | Builds the original `src/` classes and tests in place, without modifying them |

ArchUnit verifies framework-free core modules, inward dependencies, adapter isolation, controllers' use of bus ports rather than handlers/dispatch implementations, command/query handler separation, bootstrap ownership of transaction management, and the absence of legacy dependencies. Maven separates compilation boundaries; only bootstrap produces an executable Boot jar.

### Command and query flow

This structure adopts Doodle's synchronous command/query bus pattern within the existing module boundaries.

- **POST**: `TimeDepositController` dispatches `UpdateTimeDepositBalancesCommand` through the `CommandBus` port. Bootstrap provides `TransactionalCommandBus`, which opens a transaction before delegating to `DefaultCommandBus`. The registered `UpdateTimeDepositBalancesCommandHandler` loads and locks deposits through the domain repository port, applies the existing interest calculator, and persists balances. Commit completes before the HTTP response.
- **GET**: the controller dispatches `RetrieveTimeDepositsQuery` through `QueryBus`. `DefaultQueryBus` selects `RetrieveTimeDepositsQueryHandler`, which retrieves deposits including withdrawals. `TimeDepositApiMapper` converts them to generated response models. Query dispatch does not start a transaction. Reads use the same persisted model, so a subsequent GET sees a completed POST's updates.

The buses associate each request class with one handler in a private immutable registry. Duplicate registrations fail at construction; an unregistered request fails at dispatch. Request result types are carried by `Command<R>` and `Query<R>`; the update uses `Void` because it has no response payload. The runtime registry casts are confined to the dispatchers.

`UseCaseConfiguration` registers handlers and constructs both buses. `PersistenceAdapterConfiguration` supplies the JDBC adapter and result-set mapper, while `WebAdapterConfiguration` supplies the controller and API mapper. Spring collects registered handlers for each bus. To extend application behavior, add a typed request and handler and register the handler in bootstrap; interest-rule extensions continue to use `InterestPolicy`.

## Build and test

Run commands from this `java/` directory. Prerequisites: Maven 3.6.3+ and JDK 25 or newer with support for release 25. JDK 27 is supported for compilation using `--release 25`; verification during development used JDK 27.

```sh
mvn test       # Unit, controller/contract, architecture, and unchanged legacy tests; no Docker required
mvn verify     # Also runs PostgreSQL Testcontainers integration tests and packages the application
```

`*Test` classes run with Surefire; `*IT` classes run with Failsafe. Integration tests require a reachable Docker daemon and pull PostgreSQL `17.6-alpine`. They use isolated databases; no manually started database is required. Tests fail rather than silently skip when Docker is unavailable.

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
| GET | `/time-deposits` | 200 with all deposits and their withdrawals, ordered by IDs |
| POST | `/time-deposits/balance-updates` | 204, empty body, after crediting and persisting interest |

```sh
curl http://localhost:8080/time-deposits
curl -i -X POST http://localhost:8080/time-deposits/balance-updates
curl http://localhost:8080/time-deposits
```

Example response object:

```json
{
  "id": 1,
  "planType": "basic",
  "balance": 1000.83,
  "days": 31,
  "withdrawals": [{"id": 1, "amount": 12.34, "date": "2026-01-02"}]
}
```

The GET response is an array; deposits without withdrawals have `withdrawals: []`. Withdrawal fields are an explicit interpretation of the assignment's unspecified nested response shape.

POST is intentionally non-idempotent: every call applies another interest credit. The entire load/calculate/persist operation runs in a single transaction. Deposits are locked in ID order to serialize concurrent balance updates. Days and withdrawals remain unchanged.

### Contract-first code generation

[`docs/openapi.yaml`](docs/openapi.yaml) is the source of truth for HTTP mappings, response statuses, and response models. The web adapter runs the pinned OpenAPI Generator Maven plugin (7.26.0, Spring generator with Spring Boot 4/Jackson 3 support) automatically during `generate-sources`, including when running `mvn test`, `mvn verify`, or `mvn package`.

To generate the contract code on its own:

```sh
mvn -pl adapter/adapter-web -am generate-sources
```

Generated `TimeDepositsApi`, `TimeDepositResponse`, and `WithdrawalResponse` sources live under `adapter/adapter-web/target/generated-sources/openapi/src/main/java/`, in `org.ikigaidigital.infrastructure.web.generated` subpackages. Maven adds that directory to compilation automatically. Generated files are build output: edit the contract and regenerate rather than editing or committing them.

`TimeDepositController` implements the generated interface, dispatches typed command/query records, and delegates domain-to-response conversion to `TimeDepositApiMapper`. The generator emits abstract operation signatures so contract changes must be implemented at compile time. The contract's `double` balance, decimal withdrawal amount (`BigDecimal`), and date (`LocalDate`) mappings preserve the existing response representation.

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

## Interest compatibility and assumptions

The starter calculator is the behavior baseline, including the division by twelve:

| Exact plan name | Eligible days | Interest per invocation |
| --- | --- | --- |
| `basic` | `days > 30` | `balance * 0.01 / 12` |
| `student` | `days > 30 && days < 366` | `balance * 0.03 / 12` |
| `premium` | `days > 45` | `balance * 0.05 / 12` |

Rates are interpreted as annual percentages credited monthly. Matching is case-sensitive; unknown plan strings are returned unchanged and earn no interest.

Calculation intentionally uses double arithmetic, `new BigDecimal(interest)`, HALF_UP rounding of interest to two places, and double addition. **The final balance is not separately rounded.** For example, two basic credits on 1000 produce `1001.6600000000001`, matching the established behavior. The database uses unconstrained PostgreSQL `NUMERIC` to avoid silently rounding that result; the JDBC adapter explicitly maps decimal storage to/from the domain's double balance. Withdrawal amounts remain `BigDecimal` throughout.

New policies implement `InterestPolicy` and are supplied to `MonthlyInterestCalculator`; the use cases and adapters do not need to change.

The original classes and tests remain untouched in `src/`, compiled only by `legacy`. Integration/refactoring of those classes is deferred to a separately agreed plan. New domain tests establish independent coverage; the original placeholder test is not treated as evidence of correctness.

## Flyway schema evolution

Migrations live in `adapter/adapter-persistence/src/main/resources/db/migration/` and are loaded from that dependency's classpath by bootstrap.

- `V1__create_time_deposit_schema.sql` creates the quoted `"timeDeposits"` table and camel-case columns, `withdrawals`, required constraints, and the foreign-key index.
- Add each schema change as a new versioned file, for example `V2__add_plan_metadata.sql`.
- Do not modify an already-applied migration; startup validates recorded checksums.
- Flyway is the only schema initializer. Spring SQL initialization is disabled; no ORM schema generation is used.
- Flyway clean is disabled in application configuration. Integration tests use disposable PostgreSQL instances.

## AI-assisted workflow

See [AI_WORKFLOW.md](AI_WORKFLOW.md) for the actual harness, model, instructions, setup, and assisted work. Original implementation progress is tracked in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md); the command/query refactor checkpoints and verification results are tracked in [HEXAGONAL_REFACTOR_PLAN.md](HEXAGONAL_REFACTOR_PLAN.md).
