# AI-assisted development workflow

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
