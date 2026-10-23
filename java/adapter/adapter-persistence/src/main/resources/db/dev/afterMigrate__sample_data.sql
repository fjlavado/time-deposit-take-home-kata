-- Only the dev profile includes this callback location; normal schema history stays unchanged.
DO $$
BEGIN
    -- Seed an empty database only. Serialize concurrent dev startups and preserve all existing data.
    LOCK TABLE "timeDeposits" IN SHARE ROW EXCLUSIVE MODE;
    IF NOT EXISTS (SELECT 1 FROM "timeDeposits") THEN
        INSERT INTO "timeDeposits" ("planType", balance, days)
        VALUES ('basic', 1000, 30),
               ('basic', 1000, 31),
               ('student', 1000, 30),
               ('student', 1000, 31),
               ('student', 1000, 365),
               ('student', 1000, 366),
               ('premium', 1000, 45),
               ('premium', 1000, 46),
               ('basic', 18, 31),
               ('student', 6, 31),
               ('FuturePlan', 1000, 46),
               ('basic', 0, 31);

        -- More than one default page and more than one 100-deposit processing group.
        INSERT INTO "timeDeposits" ("planType", balance, days)
        SELECT (ARRAY['basic', 'student', 'premium'])[1 + ((n - 1) % 3)],
               1000 + n * 0.10, 60
        FROM generate_series(1, 108) AS sample(n)
        ORDER BY n;

        -- Half the deposits have no withdrawals; the others each have two dated decimal amounts.
        INSERT INTO withdrawals ("timeDepositId", amount, date)
        SELECT deposit.id, sample.amount, sample.date
        FROM "timeDeposits" AS deposit
        CROSS JOIN (VALUES (12.34, DATE '2026-01-02'), (0.10, DATE '2026-01-03')) AS sample(amount, date)
        WHERE deposit.id % 2 = 0
        ORDER BY deposit.id, sample.date;
    END IF;
END
$$;
