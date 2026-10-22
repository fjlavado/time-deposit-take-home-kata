package org.ikigaidigital.infrastructure.persistence;

import org.ikigaidigital.domain.model.PlanType;
import org.ikigaidigital.domain.model.TimeDeposit;
import org.ikigaidigital.domain.model.Withdrawal;
import org.ikigaidigital.domain.port.out.TimeDepositRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    public JdbcTimeDepositRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<TimeDeposit> findAll() {
        return jdbc.query(SELECT_ALL, (ResultSetExtractor<List<TimeDeposit>>) rows -> {
            var deposits = new LinkedHashMap<Integer, DepositRow>();
            while (rows.next()) {
                int id = rows.getInt("id");
                var deposit = deposits.get(id);
                if (deposit == null) {
                    deposit = new DepositRow(id, new PlanType(rows.getString("planType")),
                            rows.getBigDecimal("balance").doubleValue(), rows.getInt("days"), new ArrayList<>());
                    deposits.put(id, deposit);
                }
                Integer withdrawalId = rows.getObject("withdrawal_id", Integer.class);
                if (withdrawalId != null) {
                    deposit.withdrawals().add(new Withdrawal(withdrawalId, rows.getBigDecimal("amount"),
                            rows.getDate("date").toLocalDate()));
                }
            }
            return deposits.values().stream().map(DepositRow::toDomain).toList();
        });
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
                    statement.setBigDecimal(1, BigDecimal.valueOf(deposit.balance()));
                    statement.setInt(2, deposit.id());
                });
    }

    private record DepositRow(int id, PlanType planType, double balance, int days, List<Withdrawal> withdrawals) {
        TimeDeposit toDomain() {
            return new TimeDeposit(id, planType, balance, days, withdrawals);
        }
    }
}
