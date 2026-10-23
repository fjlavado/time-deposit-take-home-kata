package org.ikigaidigital.infrastructure.persistence.jdbc;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

@Table("timeDeposits")
public record TimeDepositTable(@Id Integer id, @Column("planType") String planType,
                               BigDecimal balance, int days,
                               @Version Long version,
                               @MappedCollection(idColumn = "timeDepositId") Set<WithdrawalTable> withdrawals) {
    public TimeDepositTable {
        // Single-query loading can supply null for an aggregate with no child rows.
        withdrawals = Objects.requireNonNullElseGet(withdrawals, Set::of);
    }
}
