# Performance testing

The performance environment uses PostgreSQL 17.6 in its own Compose project,
database (`time_deposits_perf`), named volume, and localhost port (default 5433).
Run these commands from `java/`. Prerequisites: Docker Compose, Maven, JDK 25+,
Python 3. Keep the application stopped when resetting the dataset. Docker must be
local for the published localhost port; on this Linux host use
`export DOCKER_HOST=unix:///var/run/docker.sock`. If 5433 is occupied, set
`export PERF_DB_PORT=55433` and use that port in `DB_URL`.

## Database and repeatable sample

```sh
docker compose -f performance/compose.yaml up -d --wait
mvn package
DB_URL=jdbc:postgresql://localhost:5433/time_deposits_perf \
  java -Xms256m -Xmx512m -jar bootstrap/target/bootstrap-1.0-SNAPSHOT.jar \
  --spring.main.web-application-type=none
```

This non-web startup applies Flyway and exits. Then seed:

```sh
docker compose -f performance/compose.yaml exec -T postgres \
  psql -X -U time_deposit -d time_deposits_perf \
  -v deposits=100000 -v max_withdrawals=10 < performance/seed.sql
```

Every seed resets deposits, withdrawals, identities, and execution history in one
transaction, then runs `ANALYZE`. It never modifies Flyway history. The default
contains 100,000 deposits and approximately 500,000 withdrawals. Set `deposits`
to 1000000 for the larger sample, or change `max_withdrawals` (including zero).
Plan/age cycles cover all three plans at days 0, 30, 31, 45, 46, 365, 366, and
730. Amounts, dates, and 0..10 withdrawals per deposit are deterministic.

### Verify the seed fixture

On the migrated performance database, run twice to verify repeatable resets:

```sh
docker compose -f performance/compose.yaml exec -T postgres \
  psql -X -U time_deposit -d time_deposits_perf \
  -v deposits=24 -v max_withdrawals=10 < performance/seed.sql
docker compose -f performance/compose.yaml exec -T postgres \
  psql -X -U time_deposit -d time_deposits_perf < performance/verify-seed.sql
```

Reseed the full dataset before benchmarking. Stop PostgreSQL with
`docker compose -f performance/compose.yaml down`; its volume retains data.
`PERF_DB_PORT` overrides the published database port.
