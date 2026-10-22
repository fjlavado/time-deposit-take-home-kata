package org.ikigaidigital.infrastructure.persistence;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.domain.port.out.TimeDepositRepository;
import org.ikigaidigital.infrastructure.persistence.mapper.TimeDepositResultSetMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

public final class JdbcTimeDepositRepository implements TimeDepositRepository {
    private static final String SELECT_ALL = """
            SELECT t.id, t."planType", t.balance, t.days,
                   w.id AS withdrawal_id, w.amount, w.date
            FROM "timeDeposits" t
            LEFT JOIN withdrawals w ON w."timeDepositId" = t.id
            ORDER BY t.id, w.id
            """;

    private final JdbcTemplate jdbc;
    private final TimeDepositResultSetMapper mapper;

    public JdbcTimeDepositRepository(JdbcTemplate jdbc, TimeDepositResultSetMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public List<TimeDeposit> findAll() {
        return jdbc.query(SELECT_ALL, mapper);
    }

    @Override
    public List<TimeDeposit> findAllForBalanceUpdate() {
        // The outer use-case transaction holds locks until all balances are saved.
        // Stable lock order serializes concurrent updates without lost interest credits.
        jdbc.queryForList("SELECT id FROM \"timeDeposits\" ORDER BY id FOR UPDATE", Integer.class);
        return findAll();
    }

    @Override
    public void saveBalances(List<TimeDeposit> deposits) {
        if (deposits.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("UPDATE \"timeDeposits\" SET balance = ? WHERE id = ?", deposits, 100,
                (statement, deposit) -> {
                    // Decimal storage, explicit conversion at the boundary to compatible double arithmetic.
                    statement.setBigDecimal(1, BigDecimal.valueOf(deposit.getBalance()));
                    statement.setInt(2, deposit.getId());
                });
    }
}
