-- Behavioral fixture assertions; execute after seeding 24 deposits / max_withdrawals=10.
\set ON_ERROR_STOP on
DO $$
BEGIN
    IF (SELECT count(*) FROM "timeDeposits") <> 24
        OR (SELECT count(*) FROM withdrawals) <> 113 THEN
        RAISE EXCEPTION 'Unexpected fixture sizes';
    END IF;
    IF (SELECT count(DISTINCT ("planType", days)) FROM "timeDeposits") <> 24
        OR EXISTS (SELECT FROM "timeDeposits" WHERE version <> 0)
        OR EXISTS (SELECT FROM balance_update_executions) THEN
        RAISE EXCEPTION 'Missing plan/boundary combination or reset failure';
    END IF;
    IF (SELECT count(*) FROM "timeDeposits" d LEFT JOIN withdrawals w
        ON w."timeDepositId" = d.id WHERE w.id IS NULL) <> 2
        OR (SELECT count(*) FROM withdrawals WHERE "timeDepositId" = 10) <> 10 THEN
        RAISE EXCEPTION 'Withdrawal distribution incorrect';
    END IF;
    IF NOT EXISTS (SELECT FROM "timeDeposits" WHERE id = 1 AND "planType" = 'basic'
        AND days = 0 AND balance = 179.19)
        OR NOT EXISTS (SELECT FROM withdrawals WHERE id = 1 AND "timeDepositId" = 1
            AND amount = 0.49 AND date = DATE '2025-12-30') THEN
        RAISE EXCEPTION 'Deterministic value mapping incorrect';
    END IF;
    -- Identity sequences must also support subsequent ordinary inserts.
    IF nextval(pg_get_serial_sequence('"timeDeposits"', 'id')) <> 25
        OR nextval(pg_get_serial_sequence('withdrawals', 'id')) <> 114 THEN
        RAISE EXCEPTION 'Identity sequence reset incorrect';
    END IF;
END $$;
