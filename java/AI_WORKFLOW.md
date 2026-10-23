# AI-assisted development workflow

## Strict hexagonal ownership — 2026-10-23

The user approved the architecture audit and requested implementation, atomic
commits, and consolidation of Java Markdown documentation under `docs/`.
OpenCode (`openai/gpt-6.1-sol`) uses local read/search/patch tools, Git, Maven
3.9.4, JDK 27 (release 25), and PostgreSQL Testcontainers through the local Docker
socket. No subagents or additional skills are used.

The first checkpoint moves repository ports to `application.port.out`, paging
contracts to `application.pagination`, and job state to `application.job`.
Persistence now depends inward on application; web is forbidden from accessing
outbound ports. `mvn -q clean test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn -q verify` passed before committing.

## Repeatable performance fixtures

OpenCode (`openai/gpt-6.1-sol`) implemented the user-approved performance plan
under `java/performance/`, following `AGENTS.md`. The first increment adds an
isolated PostgreSQL Compose project and parameterized deterministic reset/seed
SQL. Real PostgreSQL fixture checks passed twice, covering row counts, all 24
plan/age combinations, empty/heavy withdrawal collections, values/dates, clean
versions/audit history, and identity sequences. `DOCKER_HOST=unix:///var/run/docker.sock
mvn -q verify` also passed. The published database port was changed locally to
55433 because 5433 was occupied. Commands and assumptions are in
`performance/README.md`; this increment adds no schema migration.

The next increment implements the standalone Gatling Java module, opt-in Maven
profile, and standard-library Python evaluation runner. Official Gatling docs,
plugin source, and Maven Central metadata informed the pinned versions and API
configuration. Real HTTP smoke/baseline/mixed/recovery verification passed with
24 deposits and 113 withdrawals: two accepted executions completed, twenty busy
responses matched their UUID, and immutable data/version checks passed. A
deliberate zero-millisecond p95 budget returned nonzero and retained failure
artifacts, verifying that failed assertions cannot appear as a passing run.
Three Python reporting regressions passed, covering Gatling 3.16 HTML metrics
and refusal to silently accept missing/changed report data. `mvn -q test` passed.
The runner archives reports because this plugin pins its results directory, and
extracts the HTML table because Gatling 3.16 writes binary simulation logs.

The first full 100,000-deposit / 500,005-withdrawal run exposed real saturation:
50 GET/s passed, while mixed load timed out extensively. A timed-out POST was
admitted later after the runner briefly observed no active audit. This drove a
measurement correction: require a quiet database window before recovery, capture
final audits/data after graceful shutdown, expose unacknowledged audit UUIDs,
and check audit intervals for overlap. Two regression checks cover late admission
and bounded waits; all five Python checks and the corrected real-HTTP quick
evaluation passed. The stress workload and budgets were not reduced to hide the
observed failures. Full `mvn -q -Pperformance verify` also passed before that run.

The corrected default evaluation finished in about nine minutes on 2026-10-23:
100,000 deposits / 500,005 withdrawals, baseline GET p95 165 ms, mixed GET p95
10,001 ms with 6,968/11,000 timeouts, and recovery GET p95 155 ms with no errors.
Three executions completed without overlap, including one unacknowledged late
admission; all immutable data fingerprints matched and every version reached 3.
The runner correctly returned exit code 1 for failed stress budgets and UUID
reconciliation. `performance/RESULTS.md` preserves actual metrics, environment,
resource observations, interpretation, and follow-up experiments; raw HTML/JSON,
GC/application logs, and resource samples remain under the ignored evaluation
target directory. No production performance tuning was performed as part of
this evaluation. User-requested atomic commits separate fixtures, harness,
measurement correction, and measured results.

## Background jobs and database-backed admission

The latest user request was implemented with OpenCode, `openai/gpt-6.1-sol`, local
read/patch/terminal tools, Maven/JDK 27, and the repository's `AGENTS.md`. Reproduce
the task with:

> Make the existing balance-update POST start a background job. Return 202 STARTED
> with a UUID, or 409 RUNNING with the active UUID while busy. After completion/failure,
> another POST starts a new execution. Persist a minimal execution log and use the
> database to prevent racing handlers/application instances from both starting work.
> Keep exactly two business endpoints, explain why no log-query endpoint is added,
> update OpenAPI, verify with PostgreSQL/Testcontainers, and create atomic commits.

AI-assisted work covers the UUID audit migration, PostgreSQL partial-unique-index
reservation, framework-free command/result/ports, executor lifecycle and transaction
wiring, generated API contract/controller updates, deterministic concurrent-handler
and real-HTTP integration coverage, and scope documentation. The existing calculator
and bounded streaming update service are reused. No additional skills/subagents or
provider setup were needed. Design, SQL inspection examples, and operational behavior
are in [BACKGROUND_BALANCE_UPDATES.md](BACKGROUND_BALANCE_UPDATES.md).

The database/job foundation passed `mvn -q test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn -q verify`; the asynchronous API checkpoint
also passed those commands. Verification corrected the existing schema-version assertion
for V4 and a Swagger-parser raw-schema type in the new UUID contract assertion.
Final clean-build verification passed `mvn -q clean test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn -q verify`: 160 non-integration cases and
35 PostgreSQL integration cases, with no failures, errors, or skips.

## Tools actually used

- Harness: OpenCode, running in this repository with filesystem patch/read tools and terminal access.
- Model: OpenAI `openai/gpt-6.1-sol`.
- Repository instructions: [`AGENTS.md`](AGENTS.md), plus the assignment in the repository-root README.
- Tracking and design: [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) and [`HEXAGONAL_REFACTOR_PLAN.md`](HEXAGONAL_REFACTOR_PLAN.md).
- Hound HTTP fetches: checked Maven Central artifact availability and metadata for Spring Boot 4.1.1, ArchUnit, and the Swagger parser.
- Verification: Maven, JDK 27 with release 25, JUnit, AssertJ, ArchUnit, and real PostgreSQL Testcontainers tests through the local Docker socket.

No subagents, additional skills, custom MCP servers, or agent plugins were used for this implementation. No credentials or private provider configuration are checked in.

## Reproduce the workflow

1. Clone the personal fork and open its root in OpenCode.
2. Select `openai/gpt-6.1-sol` using an authenticated provider supported by your OpenCode installation.
3. Read the root assignment, `java/AGENTS.md`, and `java/IMPLEMENTATION_PLAN.md` as project context.
4. Use the following task prompt, reflecting the user's agreed constraints:

   > Implement the tracked Time Deposit backend plan incrementally under java/. Target Java 25 using JDK 27 and Spring Boot 4.1.1. Enforce hexagonal boundaries with Maven modules. Keep all existing java/src/ classes and tests unchanged; their refactoring is deferred. Use PostgreSQL and Flyway versioned SQL migrations, exactly the two documented REST operations, and framework-free domain/application code. Verify each layer and the final reactor with meaningful tests, including real PostgreSQL Testcontainers coverage. Update the tracking document with actual results. Keep changes ready for atomic commits; do not commit unless explicitly requested.

5. Run the documented Maven commands from `java/`. For this environment, select the functioning local Docker daemon with `DOCKER_HOST=unix:///var/run/docker.sock` when running integration tests.
6. Review generated code, migrations, API contract, architecture checks, and test results before committing.

The custom project rules used by the agent are versioned in `AGENTS.md`; the prompt above records the implementation-specific steering. Provider authentication and global harness settings remain local to the developer.

## Assisted work and rationale

The agent assisted with the module design, Maven configuration, immutable domain types and policies, ports/interactors, JDBC mapping, transaction wiring, Flyway migration, REST DTOs/controllers, OpenAPI contract, tests, and operational documentation. These are repetitive or cross-layer tasks where explicit dependency boundaries and executable checks help catch inconsistencies.

Verification drove corrections: an end-to-end assertion initially expected a cent-rounded repeated basic balance. The established arithmetic actually yields `1001.6600000000001`; the assertion was corrected and a domain regression assertion added, preserving the implementation's intended compatibility rather than rounding production results.

The existing starter code was inspected as the behavioral baseline and remains unchanged, as requested. Human review is supported by the seven implementation commits in the tracking document and by independent unit, architecture, contract, transaction, and real-HTTP tests.

After the user explicitly requested commits, each increment was staged separately and exported using `git checkout-index` to `/tmp/opencode/time-deposit-commit-check/`. Maven verification ran against those exported snapshots before committing, ensuring an increment did not accidentally rely on files reserved for a later commit. The complete API checkpoint passed all 47 cases. Documentation was reviewed and whitespace-checked in a separate commit.

The subsequent user-requested adapter refactor was also AI-assisted: both adapter modules were moved under an `adapter` Maven parent/aggregator, with parent inheritance, the OpenAPI test-resource path, and module documentation updated together.

The contract-first refactor was AI-assisted in response to the user's request to generate the web API from `java/docs/openapi.yaml`. OpenCode used Hound to check the published OpenAPI Generator version and Spring generator options, configured Maven generation, replaced handwritten response DTOs and HTTP mappings with generated models and an interface, and adapted the controller and existing contract checks. Reproduce generation with `mvn -pl adapter/adapter-web -am generate-sources`; the pinned plugin configuration is in `adapter/adapter-web/pom.xml`.

## Doodle-inspired hexagonal refactor

The user asked to use the local `~/bitbucket/doodle` project as an architecture reference,
confirmed that this repository's Java implementation was the target, selected Doodle-style
command/query buses, and requested a dedicated step-by-step Markdown plan and meaningful
commits. OpenCode inspected Doodle's architecture documentation, bus implementations,
handlers, repository ports/adapters, and controllers through local filesystem reads.

The agreed task prompt can be reproduced as:

> Apply java/HEXAGONAL_REFACTOR_PLAN.md using Doodle-style typed command/query records,
> dedicated handlers, and synchronous dispatch buses. Preserve all Maven modules, the
> framework-free domain/application core, interest behavior, two generated HTTP operations,
> ordered locking, and transactional bulk updates. Extract adapter mappers, wire command
> transaction decoration in bootstrap, migrate verification, and record actual results.
> Leave original java/src/ classes and tests unchanged. Create meaningful, verified commits.

The same OpenCode harness and `openai/gpt-6.1-sol` model assisted with the plan, typed
bus/handler implementation, mapper extraction, bootstrap/controller rewiring, removal
of superseded application services, and test/documentation updates. Result-typed requests
and private immutable registries adapt Doodle's dispatch pattern to the framework-free
core. Existing PostgreSQL and HTTP tests verify compatibility; focused tests additionally
exercise routing, duplicate/missing handlers, registry immutability, and query dispatch
without transaction creation. ArchUnit enforces controller bus-port use, handler separation,
and bootstrap ownership of transaction management.

The application checkpoint passed `mvn -B -pl application -am verify`. The switched
backend passed `mvn -B clean test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify`: 44 non-integration cases and
11 real PostgreSQL integration cases, with no failures, errors, or skips. Checkpoints
were reviewed and verified before their Conventional Commits; commit IDs and final
checks are recorded in `HEXAGONAL_REFACTOR_PLAN.md`.

## Starter calculator Strategy refactor

The user requested a Markdown plan, a Strategy-based refactor of
`java/src/main/java/org/ikigaidigital/TimeDepositCalculator.java`, and comprehensive
associated tests. After the user clarified the Java scope, the preliminary Kotlin
plan was removed and a clean working tree confirmed before Java work began.

Reproduce the task with:

> Follow java/CALCULATOR_REFACTOR_PLAN.md. Establish behavioral coverage against the
> original Java calculator, then extract injectable plan strategies while preserving
> the shared model, no-argument construction, updateBalance signature, exact double
> arithmetic, and in-place updates. Cover plan boundaries, rounding, batching,
> repeated updates, and extensibility. Run the Java reactor tests and verification.

The same OpenCode harness and `openai/gpt-6.1-sol` model assisted with the plan,
characterization tests, strategy extraction, extension tests, and documentation.
Maven/JUnit verified 60 characterization cases before production changes; the
completed refactor passed 120 non-integration cases (77 in legacy) and 11 real
PostgreSQL integration cases. Commands and results are recorded in
`CALCULATOR_REFACTOR_PLAN.md`. Commits were deferred until the user's subsequent
explicit delivery request.

The user then requested an explicit no-interest strategy and a `resolve` operation.
OpenCode added `NoInterestStrategy` and `InterestStrategyResolver`, moving registry
ownership and fallback selection out of the calculator so it always resolves and
executes an `InterestStrategy`. Thirteen resolver cases and three no-interest cases
were added. `mvn -B test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify` passed 136 non-integration
cases (93 in legacy) and 11 PostgreSQL integration cases, with no failures or skips.

The next user refinement made `resolve` deposit-aware: match the plan, evaluate
the strategy's eligibility assertion, and return `NoInterestStrategy` if either
matching or eligibility fails. OpenCode introduced `isApplicable(TimeDeposit)`,
moved the built-in age guards into it, adapted the resolver/calculator and custom
plan example, and expanded the tests to verify boundary resolution, full-deposit
assertions, and separation from interest calculation. `mvn -B test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify` passed 163 non-integration
cases (120 in legacy) and 11 PostgreSQL integration cases, without failures or skips.

## Review and balance value-object refactor

The user requested review of their latest changes followed by value-object
encapsulation of the starter deposit's balance and a domain update method.
OpenCode inspected tracked diffs and the untracked strategy files, preserving the
user's `calculateInterestRounded` extraction. The existing legacy tests exposed
two half-cent regressions caused by `BigDecimal.valueOf`; both were reproduced
before restoring the established `new BigDecimal` conversion.

The same harness/model introduced the immutable `Balance` record, with value
equality and addition returning a new value, and `TimeDeposit.updateBalance` to
credit already-rounded interest. The deposit retains its identity and compatible
constructor/getters/setter. The calculator now delegates the mutation, with no
external read/add/set operation. Focused tests cover equality, immutability,
floating-point compatibility, replacement setters, independent deposit state,
and direct rounding-helper behavior. Documentation was updated to reflect the
user's rounding helper and the new state-encapsulation boundary.

`mvn -B test` and `DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify` passed
178 non-integration cases (135 in legacy) plus 11 PostgreSQL integration cases,
without failures, errors, or skips.

On the user's subsequent request for meaningful commits, OpenCode reviewed the
working tree, staged diffs, and recent Conventional Commit history. The Strategy
checkpoint was staged with the compatible read/add/set balance call; the separate
balance-value checkpoint switches that call to `deposit.updateBalance`. Each code
checkpoint was exported from the Git index to a fresh `/tmp/opencode/` directory
and passed full Maven verification with local PostgreSQL Testcontainers before
committing. The first snapshot reproduced three rounding regressions after the
helper had returned to `BigDecimal.valueOf`; the established conversion was restored
and verification passed before committing. Commit IDs and case counts are recorded
in `CALCULATOR_REFACTOR_PLAN.md`; delivery documentation is committed separately.

## Domain consolidation

On the user's request to move `legacy` into `domain` and remove unnecessary policy
code, OpenCode with `openai/gpt-6.1-sol` moved the shared sources and tests into
`domain/src/`, retained their public packages, and removed the `legacy` Maven module.
At that checkpoint, `MonthlyInterestCalculator` adapted immutable backend deposits to the shared
Strategy calculator instead of duplicating eligibility, rates, or rounding through
`InterestPolicy` and `AnnualRateInterestPolicy`. Architecture checks include the
compatible public packages in the framework-free domain layer and confine their
use to that layer. Existing behavioral tests and the backend extension test verify
the unified calculation path; agent guidance and module documentation were updated.

`mvn -B clean test` and `DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify`
passed on JDK 27 with release 25: 178 non-integration cases (160 in domain) and
11 real PostgreSQL integration cases, without failures, errors, or skips.

## Single deposit model

On the user's request to simplify the two `TimeDeposit` classes, OpenCode with
`openai/gpt-6.1-sol` retained `org.ikigaidigital.TimeDeposit` as the single domain
model, preserving its original constructor and mutable balance API. It added a
constructor and getter for defensively copied, read-only withdrawals, migrated
application and adapters to that model, and removed the duplicate backend record,
`PlanType` wrapper, and `MonthlyInterestCalculator` conversion layer. The command
handler now calls `TimeDepositCalculator` directly. Architecture rules and agent
guidance reflect the shared domain model. Redundant adapter-calculator tests were
removed in favor of existing strategy/rounding coverage; withdrawal immutability
and pre-mutation metadata snapshots cover the model consolidation.

`mvn -B clean test` and `DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify`
passed on JDK 27 with release 25: 155 non-integration cases (137 in domain) and
11 real PostgreSQL integration cases, without failures, errors, or skips. The
integration suite covers API responses, persistence mappings, transaction rollback,
and concurrent balance updates.

## Spring Data JDBC migration

The user requested Spring Data JDBC in `java/adapter/adapter-persistence/`, using
the local Doodle `infrastructure/jdbc` package as a reference, then approved the
migration plan and requested implementation and documentation. OpenCode with
`openai/gpt-6.1-sol` inspected Doodle's annotated tables, repository interfaces,
and mappers through filesystem tools. The reference informed the structure;
this repository's Maven build remains self-contained.

Reproduce the task with:

> Replace persistence templates with Spring Data JDBC table records, repository
> interfaces, and a dedicated domain mapper. Keep ordered bulk reads and row
> locks, explicit decimal/date conversion, balance-only writes, and the existing
> command transaction boundary. Enable repository scanning in bootstrap, adapt
> real PostgreSQL integration coverage, and document the changes and verification.

The agent assisted with dependency changes, annotated records, repository query
methods, mapper/adapter rewiring, bootstrap scanning, and integration-test setup
using `AbstractJdbcConfiguration` and real repository proxies. Existing mapping
tests were strengthened with out-of-order fixtures, withdrawals belonging to
different deposits, and preservation of withdrawal IDs/amounts/dates after writes.
Spring Data modifying queries issue one balance update per deposit, replacing
the previous template batch operation; all writes share the outer transaction.
The Java README, implementation tracker, and project guidance document this design.

`mvn -B clean test` and `DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify`
passed on JDK 27 with release 25: 155 non-integration cases and 11 PostgreSQL
integration cases, without failures, errors, or skips. Verification covers actual
Spring Data mappings/queries, HTTP behavior, transaction rollback, and serialized
concurrent updates. No additional skills or subagents were used.

The user then required `@MappedCollection` on `TimeDepositTable`. OpenCode removed
the independent withdrawal repository/read and mapped the child set using the
existing `timeDepositId` foreign key. Hound fetched the official Spring Data
aggregate-loading documentation, and `javap` checked the installed Spring Data
4.1.1/Boot APIs. Since mapped collections alone issue additional child queries,
bootstrap now enables single-query loading on `JdbcMappingContext`; the repository
uses the supported base `findAll()` method and mapping sorts IDs in memory.

PostgreSQL integration tests count actual repository read invocations through a
delegating JDBC-operations proxy and assert one aggregate query for empty and
populated databases. The initial focused run exposed null child sets for deposits
without withdrawals; the aggregate record now normalizes them to an empty set.
The focused adapter verification then passed, followed by `mvn -B clean test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify`: all 155 non-integration
cases and 11 PostgreSQL integration cases passed without failures, errors, or
skips. Bootstrap coverage also verifies that single-query loading is enabled.

## Inherited CRUD and paginated search

The user requested inherited CRUD members and paginated search. After clarification,
they selected **CRUD only in persistence** and **paginated GET**. OpenCode with
`openai/gpt-6.1-sol` preserved the user's change to `CrudRepository` and removed
unused imports, while keeping the domain port independent of Spring Data. The port
now uses framework-free `PageRequestVO` and `Page<TimeDeposit>` values. The query,
generated OpenAPI interface/DTOs, controller, and tests carry page parameters and
metadata end to end. OpenAPI 2.0.0 documents the new GET page envelope.

The agent checked installed Spring Data 4.1.1 repository bytecode using `javap` and
revisited the official aggregate-loading documentation using Hound. A focused
PostgreSQL query-count test demonstrated that inherited `findAll(Pageable)` on the
aggregate falls back to extra child reads. The implemented solution uses inherited
paging over an ID-only record of the same table, followed by inherited
`findAllById` with single-query loading for the page's mapped withdrawals. No custom
`findAll` declaration, templates, or schema changes are needed. Test instrumentation
now observes both prepared reads and plain JDBC count statements.

The update handler locks once, then calculates/saves pages of 100 deposits inside
the command transaction. Tests cover processing all 201/205 deposits exactly once
and rollback when the second page fails. The web adapter's validation starter was
added after generated minimum constraints exposed the missing validation provider.
Build verification then passed `mvn -B clean test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify` on JDK 27 with release 25:
158 non-integration cases and 14 PostgreSQL integration cases, without failures,
errors, or skips. The README, implementation tracker, and agent rules document
pagination, inherited repository usage, query bounds, and transaction behavior.

## Atomic typed-plan checkpoint — 2026-10-23

The user requested analysis and meaningful atomic commits, then approved the plan.
OpenCode with `openai/gpt-6.1-sol` used Git inspection and `apply_patch` to separate
the typed-plan/sealed-singleton migration from the decimal half-cent behavior change
and obsolete IDE metadata removal. Each code checkpoint is exported from the Git
index for independent verification, so unrelated working-tree changes cannot mask
an incomplete commit. The refactor checkpoint retains binary half-cent rounding,
migrates callers and exact-string boundary mappings, and updates architecture rules.
The README example now uses relocated imports, singleton instances, and `apply()`.

From the exported refactor checkpoint, `mvn test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn verify` passed on JDK 27 with release 25:
**153 non-integration cases** (127 in domain) and **14 PostgreSQL integration cases**,
with no failures, errors, or skips. Application packaging also succeeded.

## Typed plans and sealed strategies: test/documentation update — 2026-10-23

The user supplied domain/API/package changes and explicitly requested changes only
to tests and affected documentation. OpenCode with `openai/gpt-6.1-sol` inspected the
existing staged/unstaged work using Git and filesystem tools and used `apply_patch`
for test/documentation edits. The initial pass left production code and build configuration
as supplied; the subsequent approved scope expansion is recorded below.

Reproduce the task with:

> Update Java tests for the supplied PlanType, relocated TimeDeposit/calculator,
> Balance.value(), sealed InterestStrategy, and BigDecimal.valueOf rounding changes.
> Use permitted strategy instances instead of lambdas or anonymous implementations;
> preserve meaningful eligibility, routing, registry-copy, metadata, and rounding
> coverage. Update affected documentation and run mvn test and mvn verify. Keep all
> production code as supplied and report production blockers without test workarounds.

The assisted work migrates domain/application/adapter fixtures and assertions to
typed plans, replaces custom-implementation tests with permitted strategy aliases
and overrides, and updates ArchUnit's domain package definitions. Added coverage
checks exact plan value semantics, strategy-declared plans, string serialization,
and persisted half-cent rounding. Documentation now explains the sealed extension
boundary and the 18.02/6.02 results that differ from the original calculator.

Initial `mvn test` and `mvn verify` attempts stopped in domain production compilation,
before any tests ran: `TimeDeposit.getBalance()` called the removed `Balance.amount()`
accessor, and the calculator passed `Map<String, InterestStrategy>` to a resolver requiring
typed keys. Inspection also found that the persistence mapper passed a string to the
deposit's `PlanType` argument. The first pass reported these blockers and passed
`git diff --check` without changing production code.

When the user asked to continue, OpenCode rechecked those files and explicitly asked
whether to expand the scope. The user selected **Allow minimal production fixes**.
The agent changed only the three approved production mismatches: `balance.value()` in
the deposit getter, `Map<PlanType, InterestStrategy>` in the calculator constructor, and
`new PlanType(deposit.planType())` in the persistence mapper. No build configuration
changes were needed.

`mvn test` then passed **179 non-integration cases**. Docker availability was checked,
and `DOCKER_HOST=unix:///var/run/docker.sock mvn verify` passed those plus **15 PostgreSQL
integration cases**, including the new persisted half-cent results. All tests passed
without failures, errors, or skips on JDK 27 with release 25; application packaging also
succeeded. Documentation was updated to replace the resolved blocker status with these
results.

## Singleton strategy test cleanup — 2026-10-23

The user requested review of the latest `InterestStrategy` implementation changes,
test fixes, and removal of unnecessary tests. OpenCode with `openai/gpt-6.1-sol`
used Git diffs, filesystem tools, and `apply_patch` to adapt tests to private
constructors, public singleton constants, and `apply(TimeDeposit)` returning the
eligible strategy or the no-interest singleton. The initial `mvn test` reproduced
six test-compilation errors from calls to the now-private constructors.

The assisted changes replace those constructor calls and assert singleton identity,
fix eligibility assertions to use the expected outcome, and replace self-derived
plan-type expectations with explicit constants. Repeated day/plan cases in raw
interest calculations and resolver tests were removed. Duplicate calculator tests
for registry snapshots, empty registries, and mixed-deposit processing were removed
because the resolver and calculator suites already cover those behaviors. All 60
calculator characterization cases and direct eligibility boundaries remain.

Reproduce the task with:

> Review the singleton InterestStrategy implementations and apply(deposit) fallback.
> Fix the Java tests to use the public instances and assert eligible/ineligible
> results. Remove redundant coverage while preserving boundaries, rounding, aliases,
> overrides, registry copying, and in-place balance updates. Run mvn test and mvn verify.

`mvn test` and `DOCKER_HOST=unix:///var/run/docker.sock mvn verify` passed on JDK 27
with release 25: **153 non-integration cases** (127 in domain) and **15 PostgreSQL
integration cases**, without failures, errors, or skips. The cleanup removes 26
redundant cases. Application packaging also succeeded.

## Atomic decimal-rounding checkpoint — 2026-10-23

The second commit isolates `BigDecimal.valueOf(interest)` from the structural
refactor. Its tests cover positive/negative half-cent rounding and unchanged
final-balance arithmetic, plus HTTP responses and persisted balances for basic 18
and student 6. OpenAPI and current-status documentation describe the changed
18.02/6.02 results. The original task histories above remain historical records.

From a fresh exported index snapshot, `mvn test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn verify` passed on JDK 27 with release 25:
**153 non-integration cases** and **15 PostgreSQL integration cases**, with no
failures, errors, or skips. Application packaging also succeeded. The obsolete
IntelliJ module descriptor is handled by a separate metadata-only commit.

## Controller-to-database cleanup — 2026-10-23

The user requested a read-only review from controller to database, then approved
the cleanup plan. OpenCode with `openai/gpt-6.1-sol` traced both request flows and
checked production/test references using filesystem tools and Git. `apply_patch`
removed the superseded pessimistic-locking and individual balance-write methods
from the query port, its adapter, and the Spring Data aggregate repository, along
with the unused paging helper, import, and obsolete test scaffolding.

The decimal precision and metadata/withdrawal-preservation assertions were migrated
to the active batch adapter's PostgreSQL integration coverage, including watermark
persistence and optimistic version increments. Existing uncommitted pagination
edits were preserved. Current persistence documentation describes the read-only
query port and streaming/batch update path.

`mvn test` and `DOCKER_HOST=unix:///var/run/docker.sock mvn verify` passed on
JDK 27 with release 25: **164 non-integration cases and 26 PostgreSQL integration
cases**, without failures, errors, or skips. Application packaging also succeeded.

## Aggregate CRUD persistence and schedule value object — 2026-10-23

The user requested inherited Spring Data JDBC CRUD operations for balance updates,
full aggregate loading with withdrawals, framework-managed optimistic locking, and
an external value object for pending interest-period calculations. During planning,
they selected **eager loading of all aggregates** through `findAll()`.

OpenCode with `openai/gpt-6.1-sol` used filesystem reads, reference searches, Git,
`apply_patch`, Maven, and the local Docker socket. The assisted work adds reverse
aggregate mapping, delegates writes to `CrudRepository.saveAll()`, changes the
framework-free port to eager `findPending()`/`saveAll()`, extracts immutable
`InterestPeriodSchedule`, and removes custom SQL, row mapping, batch-count checks,
cursor setup, and driver-specific test probes. The application retains save groups
of 100 inside one transaction; all loaded aggregates are held in memory.

Behavioral tests now verify full withdrawal ownership/IDs/amounts/dates after saves,
decimal precision, watermark/version persistence, stale and deleted aggregate
rejection, whole-transaction rollback, idempotent retries, HTTP concurrency, and
schedule boundaries/overflow. Existing GET pagination query-count tests also passed.
`mvn test` and `DOCKER_HOST=unix:///var/run/docker.sock mvn verify` passed on JDK 27
with release 25: **160 non-integration cases and 27 PostgreSQL integration cases**,
without failures, errors, or skips. Application packaging succeeded.

## ID-only streaming refinement — 2026-10-23

The user clarified that pending deposits must be streamed as IDs, with bounded
aggregate loading through inherited `findAllById()`, to avoid retaining all deposits
and withdrawals in memory. This supersedes the preceding eager-loading choice.
OpenCode with `openai/gpt-6.1-sol` used filesystem tools, Git, `apply_patch`, Maven,
and local PostgreSQL Testcontainers to restore the pending query as `SELECT id`,
consume ID groups of at most 100, load complete aggregates, and retain `saveAll()`
and framework-managed optimistic locking. The service rechecks loaded watermarks.

The assisted work also restores server-side ID fetching and try-with-resources,
and adapts driver instrumentation to observe cursor fetch/closure and aggregate
reads without inspecting generated write SQL. Unit tests verify lazy consumption;
PostgreSQL tests observe one ID-only cursor, fetch size 100, autocommit off, and
101 aggregate reads/save groups for 10,005 deposits with preserved withdrawals.
Read/load/write failures close the cursor and roll back earlier saves; concurrent
HTTP behavior and idempotent retries also pass.

`mvn test` and `DOCKER_HOST=unix:///var/run/docker.sock mvn verify` passed on JDK 27
with release 25: **162 non-integration cases and 29 PostgreSQL integration cases**,
without failures, errors, or skips. Application packaging succeeded.

## Removal of incremental processing — 2026-10-23

The user requested removal of `pendingInterestDays()` and all related behavior,
and confirmed that this means removing incremental processing entirely. Each
successful update must apply the existing calculator once to every deposit at
its current age; repeated successful requests credit eligible deposits again.
This supersedes the earlier schedule and watermark design recorded above.

OpenCode with `openai/gpt-6.1-sol` used filesystem/reference tools, `apply_patch`,
Git, Maven, and PostgreSQL Testcontainers through the local Docker socket. The
assisted changes remove the schedule value object and processing-watermark API,
simplify orchestration, stream all IDs, remove obsolete persistence mapping, and
update behavioral tests, OpenAPI, and current guidance. Existing calculator rules,
bounded aggregate persistence, transaction rollback, and optimistic locking remain.
The old watermark database column is unused pending a separate forward migration;
applied V1/V2 migrations are preserved.

Verification: `mvn -q clean test` and
`DOCKER_HOST=unix:///var/run/docker.sock mvn -q verify` passed on JDK 27 with
release 25: **158 non-integration cases and 28 PostgreSQL integration cases**,
without failures, errors, or skips. Commits are requested explicitly and grouped
by behavioral removal and schema cleanup.

## Watermark schema cleanup — 2026-10-23

OpenCode with `openai/gpt-6.1-sol` completed the separate schema checkpoint using
the same filesystem, patch, Git, Maven, and local Testcontainers workflow. V3 drops
the unused processing column and its check constraint while leaving V1/V2 unchanged.
Parameterized PostgreSQL coverage upgrades populated V1 and V2 schemas, including
an old nonzero watermark and version, and asserts exact preservation of deposit
data and withdrawals, retention of version/index, absence of the obsolete column
and constraint, migration checksum validation, and a no-op second migration.

The first verification identified an existing test hardcoded to schema version 2;
its expected version was updated to 3. The subsequent full
`DOCKER_HOST=unix:///var/run/docker.sock mvn -q verify` passed **158 non-integration
cases and 29 PostgreSQL integration cases**, without failures, errors, or skips.
Application packaging succeeded on JDK 27 with release 25.
