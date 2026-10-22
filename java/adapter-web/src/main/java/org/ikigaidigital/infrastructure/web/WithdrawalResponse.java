package org.ikigaidigital.infrastructure.web;

import org.ikigaidigital.domain.model.Withdrawal;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WithdrawalResponse(int id, BigDecimal amount, LocalDate date) {
    static WithdrawalResponse from(Withdrawal withdrawal) {
        return new WithdrawalResponse(withdrawal.id(), withdrawal.amount(), withdrawal.date());
    }
}
