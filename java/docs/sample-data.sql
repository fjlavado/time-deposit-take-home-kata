-- Optional demonstration data. Apply manually after Flyway has migrated the database.
WITH deposit AS (
    INSERT INTO "timeDeposits" ("planType", balance, days)
    VALUES ('basic', 1000, 31)
    RETURNING id
)
INSERT INTO withdrawals ("timeDepositId", amount, date)
SELECT id, 12.34, DATE '2026-01-02' FROM deposit;

INSERT INTO "timeDeposits" ("planType", balance, days)
VALUES ('student', 1000, 365), ('premium', 1000, 46);
