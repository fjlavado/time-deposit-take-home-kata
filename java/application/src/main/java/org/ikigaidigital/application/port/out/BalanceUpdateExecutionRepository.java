package org.ikigaidigital.application.port.out;

import org.ikigaidigital.application.job.BalanceUpdateExecutionStatus;

import java.time.Instant;
import java.util.UUID;

/** Minimal execution audit; counting its rows counts accepted balance-update attempts. */
public interface BalanceUpdateExecutionRepository {
    /** Atomically reserve proposedId, or return the active ID. The reservation is committed before returning. */
    UUID startOrGetActive(UUID proposedId, Instant startedAt);

    /** Commit the transition independently of the balance-processing transaction. */
    void update(UUID id, BalanceUpdateExecutionStatus status, Instant finishedAt);
}
