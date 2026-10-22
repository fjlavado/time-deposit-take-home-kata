package org.ikigaidigital.infrastructure.persistence.repository;

import org.ikigaidigital.infrastructure.persistence.jdbc.TimeDepositTable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface TimeDepositJdbcRepository extends CrudRepository<TimeDepositTable, Integer> {

    @Query("SELECT id FROM \"timeDeposits\" ORDER BY id FOR UPDATE")
    List<Integer> lockAllForBalanceUpdate();

    // Update only the balance: aggregate saves could overwrite metadata or withdrawals.
    @Modifying
    @Query("UPDATE \"timeDeposits\" SET balance = :balance WHERE id = :id")
    void updateBalance(@Param("id") Integer id, @Param("balance") BigDecimal balance);
}
