package org.ikigaidigital.infrastructure.persistence.jdbc;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Table("withdrawals")
public record WithdrawalTable(@Id Integer id, BigDecimal amount, LocalDate date) {
}
