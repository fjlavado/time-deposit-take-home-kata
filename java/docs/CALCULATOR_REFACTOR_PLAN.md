# Java calculator Strategy refactor

Commands and source paths in this plan are relative to `java/`.

## Scope and compatibility

The original refactor is complete; its historical results and commit checkpoints are
recorded below. Current sources are `domain/src/main/java/org/ikigaidigital/services/TimeDepositCalculator.java`
and `domain/src/main/java/org/ikigaidigital/domain/interest/TimeDeposit.java`.

### Supplied typed/sealed refactor — 2026-10-23

The subsequent user changes introduce `PlanType` constructor/getter values, move
`Balance` into `domain.interest.vo` with a `value()` accessor, seal `InterestStrategy`
to the four built-in implementations, add strategy `planType()` declarations, and key
resolver registries by `PlanType`. `BigDecimal.valueOf(interest)` replaces the historical
binary-exact conversion: basic balance 18 becomes 18.02 and student balance 6 becomes 6.02.

The task initially covered tests and documentation only. Tests now use typed fixtures,
permitted strategy aliases/overrides rather than custom lambdas, exact plan value equality,
decimal half-cent expectations, and the relocated calculator in architecture checks.
The HTTP tests retain string plan serialization, and integration coverage checks the
changed half-cent results in both responses and stored balances.

Initial `mvn test` and `mvn verify` attempts stopped in domain production compilation.
The user then explicitly approved minimal fixes in three production files: read
`Balance.value()` from `TimeDeposit`, accept `Map<PlanType, InterestStrategy>` in the
calculator constructor, and wrap the persistence mapper's stored string in `PlanType`.

After these fixes, `mvn test` passed **179 non-integration cases** and
`DOCKER_HOST=unix:///var/run/docker.sock mvn verify` passed those plus **15 PostgreSQL
integration cases**, with no failures, errors, or skips, on JDK 27 with release 25.
The subsequent singleton strategies use private constructors, public instances, and
`apply(TimeDeposit)` eligibility/fallback. After redundant test cleanup, the latest
verification passed **153 non-integration cases** (127 in domain) and **15 PostgreSQL
integration cases**. Earlier results below apply to their historical snapshots;
the original design below
describes the pre-sealed, pre-`PlanType` API. See [README.md](README.md) for the current API.

### Original scope and compatibility

Preserve the `TimeDeposit` public API, public no-argument calculator construction,
`public void updateBalance(List<TimeDeposit> xs)`, and in-place balance updates.
The backend now uses the original compatible `TimeDeposit` model, extended with
read-only withdrawals, and invokes the Strategy calculator directly. The duplicate
backend deposit, `PlanType`, and `MonthlyInterestCalculator` adapter were removed
after the earlier consolidation of the `legacy` module and duplicate interest policies. Results and
commit checkpoints below retain the commands/module names used at the time.

## Design

- Define a functional `InterestStrategy` interface returning unrounded monthly
  interest for a `TimeDeposit`.
- Add basic, student, and premium strategies under `org.ikigaidigital.interest`.
  Each owns its eligibility and rate; existing rates are annual rates divided by 12.
- Introduce `InterestStrategyResolver.resolve(TimeDeposit deposit)` to select a
  strategy by exact, case-sensitive plan name and its `isApplicable(deposit)`
  eligibility assertion. Its default registry includes the three
  existing plans; its map constructor accepts a complete custom registry and copies
  it so caller mutations cannot affect routing. Preserve both calculator constructors
  by delegating their registry configuration to the resolver.
- Return `NoInterestStrategy` for unregistered plans or a matching strategy whose
  eligibility assertion fails. The calculator always resolves and executes a
  strategy without conditional fallback logic. Custom strategies own their
  eligibility, including any initial waiting period; unconstrained functional
  strategies apply by default. Calculations no longer contain eligibility guards.
- Keep rounding centralized in `InterestStrategy.calculateInterestRounded`:
  `new BigDecimal(interest)`, two decimals, `HALF_UP`. Do not substitute
  `BigDecimal.valueOf` or decimal-only arithmetic.
- Store an immutable `Balance` value object inside the identity-bearing
  `TimeDeposit`. Its `addInterest` returns a new value using double addition without
  final-balance rounding. `TimeDeposit.updateBalance(interest)` replaces its balance
  value, so the calculator does not read/add/set state. Retain the original
  constructor, `Double getBalance()`, and `setBalance(Double)` as compatibility APIs.

| Plan | Eligible days | Interest per update |
| --- | --- | --- |
| basic | More than 30 | `balance * 0.01 / 12` |
| student | 31–365 inclusive | `balance * 0.03 / 12` |
| premium | More than 45 | `balance * 0.05 / 12` |
| Unregistered | None | Zero |

## Steps

1. Replace the tautological starter test with characterization tests and run them
   against the original calculator before changing production code.
2. Extract strategies and make the calculator delegate through its registry.
3. Add direct strategy tests and tests of registration, overrides, centralized
   rounding, complete-registry semantics, and registry defensive copying.
4. Run the Java reactor's unit tests and complete integration verification; review
   the diff and record actual results below. Update the AI workflow record.

## Coverage

- Common day boundaries 0, 29, 30, 31 for all plans; premium 44/45/46;
  student 364/365/366; basic and premium beyond a year.
- Ordinary, zero, fractional, and large balances; rounding up/down/to zero;
  half-cent binary conversion regressions; preserve non-cent-rounded balances.
- Unknown, empty, differently capitalized, and whitespace-padded names.
- Empty collections, mixed plans and eligibility, repeated updates, object identity,
  unchanged IDs/names/days/order, and duplicate references processed per list entry.
- Unrounded strategy outputs and custom plan registration/override behavior.
- Resolver selection, no-interest fallback, and no-interest strategy non-mutation.
- Balance value equality/hash codes, immutable addition, deposit credit/replacement
  semantics, independent deposit state, and direct rounding-helper regressions.

Invalid-input handling is outside the assignment's requirements.

## Verification commands

Run from `java/` with JDK 27 (compiling for Java 25):

```sh
mvn -B -pl domain -am test
mvn -B test
DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify
```

Run `git diff --check` from the repository root.

## Results

- Completed on 2026-10-22 using JDK 27 with release 25.
- Before production changes, `mvn -B -pl legacy -am test` passed all 60
  characterization cases against the original calculator. An initial premium
  repeated-update expectation was corrected to the observed and independently
  explained result: `1004.17 + 4.18 == 1008.3499999999999`.
- After extraction, `mvn -B test` passed 120 non-integration cases, including
  77 calculator/strategy cases (60 characterization, 6 extension, 11 strategy).
- `DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify` passed those 120
  cases plus 11 real PostgreSQL integration cases, with no failures, errors, or skips.
- No changes remain under `kotlin/`; `TimeDeposit` and its public API are unchanged.
- Follow-up: added `NoInterestStrategy` and `InterestStrategyResolver.resolve`, moving
  default registrations, lookup, defensive copying, and fallback out of the calculator.
  Added 13 resolver cases and 3 no-interest strategy cases. `mvn -B test` and
  `DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify` passed: 136 non-integration
  cases (93 in legacy) plus 11 PostgreSQL integration cases, with no failures,
  errors, or skips.

- Deposit-aware refinement: resolution now checks the matching strategy's
  `isApplicable(deposit)` assertion before returning it. Built-in guards moved to
  these assertions; calculation methods only compute their rate. Tests cover
  all built-in eligibility boundaries, custom assertions using both age and balance,
  and resolution without invoking calculation. `mvn -B test` and
  `DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify` passed 163 non-integration
  cases (120 in legacy) and 11 PostgreSQL integration cases, with no failures,
  errors, or skips.

## Latest-change review and balance value object

- Reviewed the user's extraction of rounding into
  `InterestStrategy.calculateInterestRounded`. Preserved that organization.
- Found a compatibility regression: `BigDecimal.valueOf` changes basic balance 18
  to 18.02 instead of 18.01, and student balance 6 to 6.02 instead of 6.01.
  `mvn -B -pl legacy -am test` reproduced both failures before changes.
- Restored `new BigDecimal(...)`, corrected the helper's rounded/unrounded Javadoc,
  and removed its unused SQL import.
- Introduced immutable `Balance`, with amount-based record equality and addition
  returning a new value. `TimeDeposit` retains identity and its existing public API,
  delegates addition to `Balance`, and exposes `updateBalance(double interest)`.
- Updated the calculator to call that method with strategy-rounded interest.
- Added 6 balance cases, 4 deposit cases, and 5 rounding-helper regression cases.
- `mvn -B test` and `DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify` passed
  178 non-integration cases (135 in legacy) and 11 PostgreSQL integration cases,
  with no failures, errors, or skips.

## Delivery checkpoints

After the user requested meaningful commits, the implementation was split into
independently buildable checkpoints. Each code checkpoint was exported from the
Git index using `git checkout-index` into a fresh directory under `/tmp/opencode/`,
then verified with `DOCKER_HOST=unix:///var/run/docker.sock mvn -B verify`.

| Commit | Summary | Verification |
| --- | --- | --- |
| `605d292` | `refactor(interest): resolve eligible deposit strategies` | 168 non-integration cases (125 in legacy), 11 PostgreSQL integration cases |
| `82ac490` | `refactor(java): encapsulate deposit balance in a value object` | 178 non-integration cases (135 in legacy), 11 PostgreSQL integration cases |

The initial staged Strategy snapshot reproduced three failures because the rounding
helper had returned to `BigDecimal.valueOf`. Restoring `new BigDecimal` resolved
them before any commit. Both final code snapshots passed without failures, errors,
or skips. The supporting plan, README, agent guidance, and workflow record are
delivered in a separate documentation commit after content and whitespace review.
