package org.ikigaidigital.application.port.out;

import org.ikigaidigital.application.job.BalanceUpdateExecutionStatus;

import java.time.Instant;
import java.util.UUID;

/** Minimal execution audit; counting its rows counts accepted balance-update attempts. */
public interface BalanceUpdateExecutionRepository {
    /** Atomically reserve proposedId, or return the existing active execution's ID. */
    UUID startOrGetActive(UUID proposedId, Instant startedAt);

    void update(UUID id, BalanceUpdateExecutionStatus status, Instant finishedAt);
}
