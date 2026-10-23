package org.ikigaidigital.infrastructure.persistence;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.port.out.TimeDepositRepositoryPort;
import org.ikigaidigital.infrastructure.persistence.mapper.TimeDepositPersistenceMapper;
import org.ikigaidigital.infrastructure.persistence.repository.TimeDepositBalanceJdbcRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

public final class TimeDepositRepositoryAdapter implements TimeDepositRepositoryPort {
    private static final String UPDATE_BALANCE = """
            UPDATE "timeDeposits"
            SET balance = ?, last_processed_days = ?, version = version + 1
            WHERE id = ? AND version = ?
            """;

    private final TimeDepositBalanceJdbcRepository deposits;
    private final TimeDepositPersistenceMapper mapper;
    private final JdbcOperations jdbc;

    public TimeDepositRepositoryAdapter(TimeDepositBalanceJdbcRepository deposits,
                                        TimeDepositPersistenceMapper mapper, JdbcOperations jdbc) {
        this.deposits = deposits;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    @Override
    public Stream<TimeDeposit> streamPending() {
        return deposits.streamPending().map(mapper::toDomain);
    }

    @Override
    public void saveBatch(List<TimeDeposit> batch) {
        if (batch.isEmpty()) {
            return;
        }
        // @Version covers aggregate saves. Custom, balance-only batch SQL must
        // explicitly compare/increment that same version and validate every count.
        var counts = jdbc.batchUpdate(UPDATE_BALANCE, batch, 100, (statement, deposit) -> {
            statement.setBigDecimal(1, BigDecimal.valueOf(deposit.getBalance()));
            statement.setInt(2, deposit.getLastProcessedDays());
            statement.setInt(3, deposit.getId());
            statement.setLong(4, deposit.getVersion());
        });
        for (var group : counts) {
            for (int count : group) {
                // Fail closed if a driver cannot confirm a single updated row.
                if (count != 1) {
                    throw new OptimisticLockingFailureException("Stale time deposit in balance update batch");
                }
            }
        }
    }
}
