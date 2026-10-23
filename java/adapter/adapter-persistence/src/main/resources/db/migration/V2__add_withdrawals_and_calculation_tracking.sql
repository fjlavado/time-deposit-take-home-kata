-- V1 already creates withdrawals and its foreign-key index. Preserve the kata's
-- existing quoted table/column names and the checksums of applied migrations.
ALTER TABLE "timeDeposits"
    ADD COLUMN last_processed_days INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT time_deposits_processed_days_nonnegative CHECK (last_processed_days >= 0),
    ADD CONSTRAINT time_deposits_version_nonnegative CHECK (version >= 0);

-- Existing balances are treated as the opening principal for tracked processing.
-- Their previously credited history cannot be inferred from the legacy schema.
