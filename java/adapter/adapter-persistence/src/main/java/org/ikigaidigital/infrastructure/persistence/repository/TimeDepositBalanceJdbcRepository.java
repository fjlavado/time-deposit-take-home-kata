package org.ikigaidigital.infrastructure.persistence.repository;

import org.ikigaidigital.infrastructure.persistence.jdbc.BalanceUpdateRowMapper;
import org.ikigaidigital.infrastructure.persistence.jdbc.TimeDepositTable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

import java.util.stream.Stream;

public interface TimeDepositBalanceJdbcRepository extends Repository<TimeDepositTable, Integer> {
    @Query(value = """
            SELECT id, "planType", balance, days, last_processed_days, version
            FROM "timeDeposits"
            WHERE days > last_processed_days
            ORDER BY id
            """, rowMapperClass = BalanceUpdateRowMapper.class)
    Stream<TimeDepositTable> streamPending();
}
