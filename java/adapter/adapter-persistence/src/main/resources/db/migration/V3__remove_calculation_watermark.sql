-- Incremental processing has been removed. Preserve existing balances and the
-- independent optimistic-lock version, without rewriting applied migrations.
ALTER TABLE "timeDeposits"
    DROP CONSTRAINT time_deposits_processed_days_nonnegative,
    DROP COLUMN last_processed_days;
