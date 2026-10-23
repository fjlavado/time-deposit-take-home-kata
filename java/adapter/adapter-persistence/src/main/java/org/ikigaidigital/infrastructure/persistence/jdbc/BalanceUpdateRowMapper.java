package org.ikigaidigital.infrastructure.persistence.jdbc;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

/** Calculation never uses withdrawals; loading them would add memory use and child queries. */
public final class BalanceUpdateRowMapper implements RowMapper<TimeDepositTable> {
    @Override
    public TimeDepositTable mapRow(ResultSet row, int rowNum) throws SQLException {
        return new TimeDepositTable(row.getInt("id"), row.getString("planType"), row.getBigDecimal("balance"),
                row.getInt("days"), row.getInt("last_processed_days"), row.getLong("version"), Set.of());
    }
}
