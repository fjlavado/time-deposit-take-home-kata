package org.ikigaidigital.domain.port.out;

import org.ikigaidigital.domain.interest.TimeDeposit;

import java.util.List;
import java.util.stream.Stream;

/** Incremental balance persistence; the caller owns a transaction across the stream and all writes. */
public interface TimeDepositRepositoryPort {
    /** Ordered, lazy snapshots with days beyond their processing watermark. Must be closed by the caller. */
    Stream<TimeDeposit> streamPending();

    /**
     * Persist only balances and processing watermarks, using each snapshot's version.
     * A stale or deleted row must fail the entire transaction; never silently skip it.
     */
    void saveBatch(List<TimeDeposit> deposits);
}
