package org.ikigaidigital.infrastructure.persistence.mapper;

import org.ikigaidigital.domain.model.PlanType;
import org.ikigaidigital.domain.model.TimeDeposit;
import org.ikigaidigital.domain.model.Withdrawal;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Groups the ordered deposit/withdrawal join without issuing additional queries. */
public final class TimeDepositResultSetMapper implements ResultSetExtractor<List<TimeDeposit>> {
    @Override
    public List<TimeDeposit> extractData(ResultSet rows) throws SQLException {
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
    }

    private record DepositRow(int id, PlanType planType, double balance, int days, List<Withdrawal> withdrawals) {
        TimeDeposit toDomain() {
            return new TimeDeposit(id, planType, balance, days, withdrawals);
        }
    }
}
