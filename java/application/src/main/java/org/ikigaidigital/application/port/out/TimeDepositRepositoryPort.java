package org.ikigaidigital.application.port.out;

import org.ikigaidigital.domain.interest.TimeDeposit;

import java.util.List;
import java.util.stream.Stream;

/** Aggregate persistence; the caller owns a transaction across loading and all saves. */
public interface TimeDepositRepositoryPort {
    /** Stream all deposit IDs in order. The caller must close the stream within its transaction. */
    Stream<Integer> streamAllIds();

    /** Load a bounded group of complete aggregate snapshots, including withdrawals, ordered by ID. */
    List<TimeDeposit> findAllById(List<Integer> ids);

    /**
     * Persist complete aggregate snapshots, using each snapshot's loaded version.
     * A stale or deleted row must fail the entire transaction; never silently skip it.
     */
    void saveAll(List<TimeDeposit> deposits);
}
