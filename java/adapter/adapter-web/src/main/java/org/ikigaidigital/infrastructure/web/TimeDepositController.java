package org.ikigaidigital.infrastructure.web;

import org.ikigaidigital.application.port.in.RetrieveTimeDepositsUseCase;
import org.ikigaidigital.application.port.in.UpdateTimeDepositBalancesUseCase;
import org.ikigaidigital.domain.model.TimeDeposit;
import org.ikigaidigital.infrastructure.web.generated.api.TimeDepositsApi;
import org.ikigaidigital.infrastructure.web.generated.model.TimeDepositResponse;
import org.ikigaidigital.infrastructure.web.generated.model.WithdrawalResponse;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public final class TimeDepositController implements TimeDepositsApi {
    private final RetrieveTimeDepositsUseCase retrieve;
    private final UpdateTimeDepositBalancesUseCase update;

    public TimeDepositController(RetrieveTimeDepositsUseCase retrieve, UpdateTimeDepositBalancesUseCase update) {
        this.retrieve = retrieve;
        this.update = update;
    }

    @Override
    public List<TimeDepositResponse> retrieveTimeDeposits() {
        return retrieve.retrieveAll().stream().map(TimeDepositController::toResponse).toList();
    }

    @Override
    public void updateTimeDepositBalances() {
        // Every invocation credits interest again; this command is intentionally non-idempotent.
        update.updateBalances();
    }

    private static TimeDepositResponse toResponse(TimeDeposit deposit) {
        return new TimeDepositResponse(deposit.id(), deposit.planType().value(), deposit.balance(), deposit.days(),
                deposit.withdrawals().stream()
                        .map(withdrawal -> new WithdrawalResponse(withdrawal.id(), withdrawal.amount(), withdrawal.date()))
                        .toList());
    }
}
