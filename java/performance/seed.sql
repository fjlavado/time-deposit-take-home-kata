-- Run through psql after Flyway V4, with the application stopped.
-- This explicit reset belongs to the isolated performance database, not Flyway.
\set ON_ERROR_STOP on
\if :{?deposits}
\else
\set deposits 100000
\endif
\if :{?max_withdrawals}
\else
\set max_withdrawals 10
\endif

BEGIN;
CREATE TEMP TABLE seed_parameters AS
SELECT :'deposits'::integer AS deposits, :'max_withdrawals'::integer AS max_withdrawals;
DO $$
BEGIN
    IF EXISTS (SELECT FROM seed_parameters WHERE deposits < 1 OR max_withdrawals < 0
        OR max_withdrawals = 2147483647
        OR deposits::bigint * greatest(max_withdrawals, 1) > 2147483647) THEN
        RAISE EXCEPTION 'Seed size must be positive and fit integer IDs';
    END IF;
    IF EXISTS (SELECT FROM balance_update_executions WHERE status IN ('STARTED', 'RUNNING')) THEN
        RAISE EXCEPTION 'Wait for the active balance update before resetting';
    END IF;
END $$;
TRUNCATE withdrawals, "timeDeposits", balance_update_executions RESTART IDENTITY;

-- Independent plan/age cycles exercise every plan at every eligibility boundary.
INSERT INTO "timeDeposits" (id, "planType", balance, days)
SELECT n, (ARRAY['basic', 'student', 'premium'])[1 + (n - 1) % 3],
       (10000 + (n::bigint * 7919) % 9990000)::numeric / 100,
       (ARRAY[0, 30, 31, 45, 46, 365, 366, 730])[1 + ((n - 1) / 3) % 8]
FROM seed_parameters p CROSS JOIN LATERAL generate_series(1, p.deposits) AS n;

-- 0..10 children by default: about five per deposit, with empty and heavy aggregates.
INSERT INTO withdrawals (id, "timeDepositId", amount, date)
SELECT row_number() OVER (ORDER BY d.id, w)::integer, d.id,
       (1 + (d.id::bigint * 31 + w * 17) % 50000)::numeric / 100,
       DATE '2026-01-01' - ((d.id + w) % 730)
FROM "timeDeposits" d CROSS JOIN seed_parameters p
CROSS JOIN LATERAL generate_series(1, d.id % (p.max_withdrawals + 1)) AS w;

SELECT setval(pg_get_serial_sequence('"timeDeposits"', 'id'), max(id)) FROM "timeDeposits";
SELECT setval(pg_get_serial_sequence('withdrawals', 'id'), coalesce(max(id), 1), count(*) > 0)
FROM withdrawals;
COMMIT;
ANALYZE "timeDeposits";
ANALYZE withdrawals;
ANALYZE balance_update_executions;
SELECT (SELECT count(*) FROM "timeDeposits") AS deposits,
       (SELECT count(*) FROM withdrawals) AS withdrawals;
