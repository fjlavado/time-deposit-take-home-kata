CREATE TABLE balance_update_executions (
    id UUID PRIMARY KEY,
    status VARCHAR(16) NOT NULL CHECK (status IN ('STARTED', 'RUNNING', 'COMPLETED', 'FAILED')),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    CHECK ((status IN ('STARTED', 'RUNNING') AND finished_at IS NULL)
        OR (status IN ('COMPLETED', 'FAILED') AND finished_at IS NOT NULL))
);

-- A database-wide admission gate: queued and running jobs both reserve the only slot.
CREATE UNIQUE INDEX balance_update_one_active_execution
    ON balance_update_executions ((1)) WHERE status IN ('STARTED', 'RUNNING');
