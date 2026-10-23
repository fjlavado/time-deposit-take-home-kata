package org.ikigaidigital.infrastructure.persistence;

import org.ikigaidigital.application.job.BalanceUpdateExecutionStatus;
import org.ikigaidigital.application.port.out.BalanceUpdateExecutionRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

public final class JdbcBalanceUpdateExecutionRepository implements BalanceUpdateExecutionRepository {
    private final JdbcTemplate jdbc;

    public JdbcBalanceUpdateExecutionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID startOrGetActive(UUID proposedId, Instant startedAt) {
        while (true) {
            var inserted = jdbc.update("""
                    INSERT INTO balance_update_executions (id, status, started_at) VALUES (?, 'STARTED', ?)
                    ON CONFLICT ((1)) WHERE status IN ('STARTED', 'RUNNING') DO NOTHING
                    """, proposedId, Timestamp.from(startedAt));
            if (inserted == 1) {
                return proposedId;
            }
            var active = jdbc.queryForList("""
                    SELECT id FROM balance_update_executions WHERE status IN ('STARTED', 'RUNNING')
                    """, UUID.class);
            if (!active.isEmpty()) {
                return active.getFirst();
            }
            // READ_COMMITTED gives this statement a fresh snapshot if the winner finished
            // between the conflicting INSERT and SELECT. Retry reservation, never queue blindly.
        }
    }

    @Override
    public void update(UUID id, BalanceUpdateExecutionStatus status, Instant finishedAt) {
        var updated = jdbc.update("""
                UPDATE balance_update_executions SET status = ?, finished_at = ?
                WHERE id = ? AND status IN ('STARTED', 'RUNNING')
                """, status.name(), finishedAt == null ? null : Timestamp.from(finishedAt), id);
        if (updated != 1) {
            throw new IllegalStateException("No active balance update execution: " + id);
        }
    }
}
