package org.ikigaidigital.infrastructure.web.mapper;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.infrastructure.web.generated.model.TimeDepositResponse;
import org.ikigaidigital.infrastructure.web.generated.model.WithdrawalResponse;

public final class TimeDepositApiMapper {
    public TimeDepositResponse toResponse(TimeDeposit deposit) {
        return new TimeDepositResponse(deposit.getId(), deposit.getPlanType(), deposit.getBalance(), deposit.getDays(),
                deposit.getWithdrawals().stream()
                        .map(withdrawal -> new WithdrawalResponse(withdrawal.id(), withdrawal.amount(), withdrawal.date()))
                        .toList());
    }
}
