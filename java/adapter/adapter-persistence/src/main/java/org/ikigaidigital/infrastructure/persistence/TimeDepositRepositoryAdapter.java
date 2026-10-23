package org.ikigaidigital.infrastructure.persistence;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.port.out.TimeDepositRepositoryPort;
import org.ikigaidigital.infrastructure.persistence.jdbc.TimeDepositTable;
import org.ikigaidigital.infrastructure.persistence.mapper.TimeDepositPersistenceMapper;
import org.ikigaidigital.infrastructure.persistence.repository.TimeDepositBalanceJdbcRepository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class TimeDepositRepositoryAdapter implements TimeDepositRepositoryPort {
    private final TimeDepositBalanceJdbcRepository deposits;
    private final TimeDepositPersistenceMapper mapper;

    public TimeDepositRepositoryAdapter(TimeDepositBalanceJdbcRepository deposits,
                                        TimeDepositPersistenceMapper mapper) {
        this.deposits = deposits;
        this.mapper = mapper;
    }

    @Override
    public Stream<Integer> streamAllIds() {
        return deposits.streamAllIds();
    }

    @Override
    public List<TimeDeposit> findAllById(List<Integer> ids) {
        // Only the current ID group is materialized, with every mapped withdrawal.
        return StreamSupport.stream(deposits.findAllById(ids).spliterator(), false)
                .map(mapper::toDomain).toList();
    }

    @Override
    public void saveAll(List<TimeDeposit> deposits) {
        this.deposits.saveAll(deposits.stream().map(mapper::toTable).toList());
    }
}
