# Java Project Agent Guide

## Scope and source of truth

- Work on the Java implementation under `java/` only. Other language implementations are outside this task's scope.
- Use the repository-root `README.md` as the assignment specification. This guide translates it into Java-specific working instructions.
- Preserve the existing calculator's behavior while implementing persistence and the two required API operations.
- When requirements are ambiguous, make a logical assumption and justify it in a code comment near the relevant implementation.

## Current project

- Java 25 compilation target, Maven, Spring Boot 4.1.1, base package `org.ikigaidigital`. JDK 27 can compile the target with `--release 25`.
- Build configuration: root `pom.xml` is a multi-module parent/aggregator; see `IMPLEMENTATION_PLAN.md` for module boundaries and progress.
- Shared production code and backend domain code: `domain/src/main/java/org/ikigaidigital/`, built by the `domain` module. The `legacy` module has been removed.
- Shared and backend domain tests: `domain/src/test/java/org/ikigaidigital/`, built by the `domain` module.
- The starter calculator's separately requested Strategy refactor is documented in `CALCULATOR_REFACTOR_PLAN.md`. Preserve its compatible API and keep new backend code/tests in their corresponding modules.
- Test dependencies are centrally managed by the Boot parent.
- The starter suite now has 60 characterization cases plus 75 strategy/resolver/extension/value-object cases. Maintain meaningful behavioral assertions when adding plans.

## Compatibility requirements

- Do not introduce breaking changes to the shared `TimeDeposit` class. Preserve its constructor and public API, including its `Double` balance type.
- Preserve `public void TimeDepositCalculator.updateBalance(List<TimeDeposit> xs)` and existing callers' ability to instantiate the calculator.
- The calculator mutates each supplied deposit's balance in place. Preserve that behavior.
- The starter deposit encapsulates immutable `Balance` values. Credit strategy-rounded interest through `TimeDeposit.updateBalance(double)`; retain the original `Double` getter/replacement setter for compatibility. Shared rounding is in `InterestStrategy.calculateInterestRounded`.
- Do not increment `days`, deduct withdrawals, or introduce other balance adjustments without a requirement supporting that behavior.
- Refactor internals behind compatible interfaces. Use separate persistence models and API DTOs where needed rather than forcing database or response requirements into the shared class.

## Interest calculation: preserve observed behavior

The README explicitly states that the existing calculator is correct. Its implementation is the compatibility baseline:

| Plan value | Eligible days | Interest per update |
| --- | --- | --- |
| `basic` | `days > 30` | `balance * 0.01 / 12` |
| `student` | `days > 30 && days < 366` | `balance * 0.03 / 12` |
| `premium` | `days > 45` | `balance * 0.05 / 12` |

- All plans receive no interest during the first 30 days, including day 30.
- Student interest includes day 365 and stops at day 366. Premium interest starts on day 46.
- Plan matching currently uses the exact lowercase strings above; unrecognized non-null plan values receive zero interest.
- Although the README describes monthly interest using percentages, the existing code divides each rate by 12. Preserve this formula and document the interpretation when implementing it.
- Interest is computed using `double`, converted with `new BigDecimal(interest)`, rounded to two decimal places using `RoundingMode.HALF_UP`, converted back to `double`, and then added to the original balance. The final balance is not separately rounded.
- Changing to `BigDecimal.valueOf`, decimal-only arithmetic, or a different rounding order can change results. Establish behavioral coverage before any numerical refactoring and retain compatible results.

## Required API and persistence

- Implement exactly two business API endpoints:
  1. A RESTful operation that updates the balances of all time deposits in the database and persists the results.
  2. A GET operation that retrieves all time deposits.
- Choose and document the update HTTP method and resource paths consistently in the implementation and OpenAPI contract; the README does not prescribe them.
- Each deposit returned by GET must include `id`, `planType`, `balance`, `days`, and `withdrawals`.
- Persist these required tables and fields:
  - `timeDeposits`: integer primary key `id`, required string `planType`, required integer `days`, required decimal `balance`.
  - `withdrawals`: integer primary key `id`, required foreign key `timeDepositId`, required decimal `amount`, required date `date`.
- Map withdrawals to their owning deposit and include them in retrieval responses. The README does not specify withdrawal response fields; document any chosen representation.
- Use decimal database types for monetary fields while preserving the shared model's API and calculator behavior through explicit mapping.
- Keep the bulk read/update/persist operation transactionally consistent.
- Do not add additional business endpoints such as create, delete, single-deposit lookup, or withdrawal management. Handling invalid input and exceptions is not required for this assignment.

## Design and implementation

- Follow the preferred hexagonal architecture: keep interest rules and use cases independent of HTTP and database frameworks; connect adapters through ports.
- The backend uses typed command/query records, dedicated handlers, and synchronous buses. Controllers depend on `application.port.in.CommandBus` and `QueryBus`; bootstrap wires handlers and the transactional command-bus decorator. Follow `HEXAGONAL_REFACTOR_PLAN.md` for the completed refactor and preserve these boundaries.
- Keep the design proportional to the kata. Apply SOLID principles and introduce small, focused abstractions where they make interest rules easier to extend.
- Separate plan eligibility/rate logic from orchestration and infrastructure so future interest complexities can be added without rewriting the API or persistence layer.
- Use the shared `InterestStrategy` implementations for both calculator APIs. `MonthlyInterestCalculator` adapts immutable backend deposits to the compatible mutable calculator; do not reintroduce duplicate policies or rounding logic. Retain the original public packages for compatibility; architecture rules treat them as part of domain.
- Use an OpenAPI/Swagger contract describing both operations, response schemas, and how to invoke them.
- Keep Java package names, source layout, and coding style consistent with the existing project.

## Verification

Run commands from `java/` with a JDK supporting release 25 (including JDK 27) and Maven installed:

```sh
mvn test
mvn verify
```

- The placeholder was replaced under `CALCULATOR_REFACTOR_PLAN.md`. Keep shared calculator coverage in `domain/src/test/java/` alongside backend domain coverage, and backend application/adapter coverage in their corresponding modules.
- Cover each plan, eligibility boundaries (days 30/31, 45/46, and 365/366), rounding, unknown plan values, an empty list, and multiple deposits. Verify both expected balances and in-place updates.
- Add API and persistence integration coverage as those components are implemented: retrieval shape including withdrawals, bulk update persistence, and decimal/date mappings.
- Use Testcontainers as preferred by the README for real-database integration tests. Document Docker prerequisites and any Maven profile or lifecycle configuration introduced so the tests are reproducible.
- Verify that the contract and implementation expose exactly the two required business operations.
- Report commands run and their results accurately; if verification is blocked, state the blocker.

## Meaningful commits: apply every time

- Keep completed work ready for atomic commits. Create commits only when the user explicitly requests them, following the harness instructions.
- Keep commits atomic: one logical purpose per commit, with the implementation, relevant tests, and supporting documentation together. Split unrelated changes into separate commits and keep each commit buildable.
- Use Conventional Commit messages: `<type>(<scope>): <imperative summary>`. Use types such as `feat`, `fix`, `refactor`, `test`, `docs`, `build`, or `chore`, and a descriptive scope such as `java`, `interest`, or `api`.
- Describe the outcome rather than the editing activity. For example, use `test(interest): cover student eligibility boundaries` instead of `update tests`. Add a body when the reason, assumptions, or compatibility constraints need explanation.
- Before committing, inspect `git status`, `git diff`, and `git log --oneline -10`; review newly added files as well. Run the relevant verification and check the staged diff for unintended changes or secrets.
- Stage only files belonging to the logical change, using explicit paths. Preserve unrelated user work.
- For documentation-only changes, review content and run a whitespace check. For code changes, run the relevant tests and build checks described above. If required verification is blocked or fails, report it and resolve it before treating the change as complete.
- Do not amend commits, bypass hooks, create empty commits, or force-push. If a hook rejects a commit, fix the issue and retry with a new commit attempt.
- Report the resulting commit hash and summary when completing the task. Pushing, branching, and opening pull requests require separate explicit instructions.

## AI workflow and delivery documentation

- Use an AI harness or agent workflow throughout development, as required by the assignment. This file is part of that workflow's custom instructions.
- Record the actual tools/models, setup steps, configuration, and custom prompts or agent rules used. Keep setup practical and reproducible, without recording credentials.
- Include a brief summary of which implementation, refactoring, testing, and documentation work was AI-assisted and why.
- Provide Java-specific build, database startup, application startup, test, and Swagger invocation instructions as those capabilities are added.
- Apply the meaningful-commit workflow above throughout development.
- Follow the README's repository constraint: develop in a personal fork, not a new branch or pull request in the upstream `ikigai-digital` repository. The submission is a public repository link.
