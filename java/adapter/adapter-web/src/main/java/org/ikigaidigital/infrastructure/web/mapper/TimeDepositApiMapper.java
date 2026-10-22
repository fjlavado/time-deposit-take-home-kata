package org.ikigaidigital.infrastructure.web.mapper;

import org.ikigaidigital.domain.model.TimeDeposit;
import org.ikigaidigital.infrastructure.web.generated.model.TimeDepositResponse;
import org.ikigaidigital.infrastructure.web.generated.model.WithdrawalResponse;

public final class TimeDepositApiMapper {
    public TimeDepositResponse toResponse(TimeDeposit deposit) {
        return new TimeDepositResponse(deposit.id(), deposit.planType().value(), deposit.balance(), deposit.days(),
                deposit.withdrawals().stream()
                        .map(withdrawal -> new WithdrawalResponse(withdrawal.id(), withdrawal.amount(), withdrawal.date()))
                        .toList());
    }
}
