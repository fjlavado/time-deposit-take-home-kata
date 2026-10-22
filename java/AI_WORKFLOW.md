# AI-assisted development workflow

## Tools actually used

- Harness: OpenCode, running in this repository with filesystem patch/read tools and terminal access.
- Model: OpenAI `openai/gpt-6.1-sol`.
- Repository instructions: [`AGENTS.md`](AGENTS.md), plus the assignment in the repository-root README.
- Tracking and design: [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md).
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
