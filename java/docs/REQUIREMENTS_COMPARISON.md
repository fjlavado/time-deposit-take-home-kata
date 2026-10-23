# Assignment coverage and deliberate differences

The [root README](../../README.md) is the assignment baseline. The Java backend
implements its two business capabilities: retrieving deposits with withdrawals
and applying interest to all persisted deposits. Later user-requested refinements
introduced pagination, background execution, typed plans, and decimal half-cent
rounding. This guide makes their effect explicit for reviewers.

**Pagination and asynchronous acceptance are small deviations in HTTP interaction,
not changes to the two business capabilities.** They change how clients retrieve
the collection and when they can observe an update. The shared Java API and
rounding differences are separate compatibility changes; full compliance with
the original no-breaking-change requirement cannot be claimed.

## Coverage summary

| Assignment area | Java implementation | Qualification |
| --- | --- | --- |
| Retrieve all deposits with `id`, `planType`, `balance`, `days`, and `withdrawals` | GET `/time-deposits`; every returned deposit has the required fields | Retrieve the complete collection by traversing pages |
| Update all persisted balances | POST `/time-deposits/balance-updates` starts one transactional update | HTTP acceptance precedes completion |
| Exactly two API endpoints | Exactly two business operations in the generated OpenAPI interface and controller | Swagger UI, its assets, and the contract are documentation resources |
| Required deposit and withdrawal tables, keys, required fields, decimal amounts, and dates | PostgreSQL schema managed by Flyway | Additional internal version and execution-audit state supports concurrency |
| Plan rates and eligibility boundaries | Basic after day 30; Student after day 30 through day 365; Premium after day 45 | Original division by twelve is retained; half-cent rounding differs |
| Shared class and calculator compatibility | In-place mutation and the `updateBalance(List<TimeDeposit>)` declaration remain | Original packages and string-based model API are not preserved |
| Extensible design and clean architecture | Strategies/resolver, framework-free core, ports/adapters, and ArchUnit checks | Adding a strategy also requires updating the sealed interface's permits list |
| OpenAPI, Testcontainers, and AI workflow | Generated API contract, PostgreSQL integration tests, versioned agent rules and workflow documentation | See the [backend guide](README.md) and [AI workflow](AI_WORKFLOW.md) |

## Small HTTP API deviations

### 1. Paginated retrieval instead of one unbounded list

**Assignment baseline:** GET returns a list of all deposits with the five required
fields. The assignment does not define pagination parameters or a page envelope.

**Implemented behavior:** GET `/time-deposits?page=0&size=20` returns an ID-ordered
`content` array with `page`, `size`, `totalElements`, and `totalPages` metadata.
The parameters default to page 0 and size 20. Each deposit retains the required
fields and its complete withdrawal collection; deposits without withdrawals
return `withdrawals: []`.

**Rationale:** Pagination bounds the number of deposit aggregates loaded and
serialized per request, allowing clients to navigate a growing database without
requiring one increasingly large response. It was explicitly selected during
the later persistence/API refinement.

**Why the deviation is small:** It uses the same retrieval operation and deposit
representation, with no new business resource or filtering rule. All deposits
remain accessible. A client reads `content` and increments `page` until
`page + 1 >= totalPages`; an empty database has zero total pages.

**Client impact:** One default GET returns at most 20 deposits, not the entire
database. A client expecting a top-level array must adapt to the envelope and
iterate pages. This is a real response-contract change despite its limited scope.
Separate requests do not provide a collection-wide snapshot: a balance update
can commit between pages.

### 2. Asynchronous update acceptance instead of waiting for completion

**Assignment baseline:** An endpoint updates all balances. The README prescribes
neither its HTTP method/path nor whether processing must finish before responding.

**Implemented behavior:** POST `/time-deposits/balance-updates` returns:

- **202 Accepted** with a new `jobId`, `status: "STARTED"`, and a message when work
  is accepted.
- **409 Conflict** with the active `jobId`, `status: "RUNNING"`, and a message when
  an execution is already queued or running. This request starts no additional work.

The worker streams all deposit IDs, loads complete aggregates in groups of at most
100, applies the calculator once per deposit at its current age, and persists all
groups in one transaction. A later-group failure rolls back earlier writes.
Successful repeated executions credit eligible deposits again; days and
withdrawals are preserved.

**Rationale:** Database-wide processing can take longer as the dataset grows.
Background execution avoids tying HTTP response time to the full calculation.
Database-backed admission prevents simultaneous POSTs or application instances
from starting overlapping executions. UUID responses identify the accepted or
already-active execution. This behavior was explicitly requested in a later refinement.

**Why the deviation is small:** The same single operation still updates every
persisted deposit using the same processing use case. Its HTTP acknowledgement
describes acceptance rather than completion. Since the assignment does not demand
synchronous processing, this is an explicit interaction choice rather than a
direct contradiction of a prescribed response status.

**Client impact:** A 202 response does not prove success or completion. GET can
still show previous committed balances while work runs. Terminal status is
available in the internal execution table using the documented SQL; there is no
public status endpoint. Repeating POST is not polling: after completion it starts
another interest-crediting execution. The executor has no automatic crash replay;
recovery limitations are described in the [background-job guide](BACKGROUND_BALANCE_UPDATES.md).

## Separate compatibility differences

### Shared Java API changes

The starter exposed `org.ikigaidigital.TimeDeposit`, a constructor accepting a
string plan, and `String getPlanType()`. The current model is
`org.ikigaidigital.domain.interest.TimeDeposit`; constructors accept `PlanType`
and `getPlanType()` returns that value object. The calculator moved from
`org.ikigaidigital` to `org.ikigaidigital.services`.

These refinements consolidate the domain model and give plan identifiers explicit
value semantics. HTTP and database plan names remain strings, including unknown
names. Nevertheless, original Java callers must migrate imports, constructor
arguments, and getter usage. Retaining the calculator method's visible declaration
does not preserve source compatibility when its domain type has moved.

This is a breaking Java API change against the assignment's shared-class
constraint, even though the HTTP deposit fields are preserved.

### Half-cent interest rounding

The starter converted double interest with `new BigDecimal(interest)`. The current
strategy uses `BigDecimal.valueOf(interest)` before rounding to two decimal places
with `HALF_UP`. This rounds the double's decimal representation instead of its
exact binary floating-point value, giving the intended decimal half-up result for
the documented half-cent examples:

| Eligible deposit | Starter final balance | Current final balance |
| --- | --- | --- |
| Basic, balance 18, day 31 | 18.01 | 18.02 |
| Student, balance 6, day 31 | 6.01 | 6.02 |

The final balance is still not separately rounded. Rates, eligibility, and
in-place updates remain as before. Domain and HTTP/database tests explicitly
assert the new results. This is an observable financial behavior change, including
through GET after an update, and does not satisfy the original requirement to keep
calculator behavior unchanged. Its rationale does not establish compatibility.

## Interpretations within the assignment

- **Monthly rates:** The original `balance * rate / 12` formulas are retained.
  Rates are interpreted as annual percentages credited monthly, consistent with
  the instruction to preserve the functioning calculator.
- **Nested withdrawals:** The assignment does not specify their response fields;
  the chosen representation is `id`, `amount`, and `date`, nested under the owner.
- **Method and paths:** GET `/time-deposits` and POST
  `/time-deposits/balance-updates` fill in choices left open by the assignment.
- **Internal persistence state:** Optimistic versions and the execution audit
  supplement the required schema without adding business operations.

The precise current HTTP behavior is defined in the [OpenAPI contract](openapi.yaml).
The [backend guide](README.md) explains Swagger invocation, paging, startup, and
testing. These differences document the delivered design; they do not assert
that an assignment reviewer has waived the original compatibility constraints.
