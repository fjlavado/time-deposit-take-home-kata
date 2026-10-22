package org.ikigaidigital.infrastructure.web;

import org.ikigaidigital.domain.model.TimeDeposit;

import java.util.List;

public record TimeDepositResponse(int id, String planType, double balance, int days,
                                  List<WithdrawalResponse> withdrawals) {
    static TimeDepositResponse from(TimeDeposit deposit) {
        return new TimeDepositResponse(deposit.id(), deposit.planType().value(), deposit.balance(), deposit.days(),
                deposit.withdrawals().stream().map(WithdrawalResponse::from).toList());
    }
}
