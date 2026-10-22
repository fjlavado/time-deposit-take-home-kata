package org.ikigaidigital.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Withdrawal(int id, BigDecimal amount, LocalDate date) {
}
