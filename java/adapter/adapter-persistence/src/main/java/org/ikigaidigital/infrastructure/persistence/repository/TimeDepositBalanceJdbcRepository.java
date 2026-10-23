package org.ikigaidigital.infrastructure.persistence.repository;

import org.ikigaidigital.infrastructure.persistence.jdbc.TimeDepositTable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.stream.Stream;

public interface TimeDepositBalanceJdbcRepository extends CrudRepository<TimeDepositTable, Integer> {
    @Query("""
            SELECT id FROM "timeDeposits"
            ORDER BY id
            """)
    Stream<Integer> streamAllIds();
}
