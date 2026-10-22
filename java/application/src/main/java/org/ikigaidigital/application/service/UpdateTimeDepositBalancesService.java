package org.ikigaidigital.application.service;

import org.ikigaidigital.application.port.in.UpdateTimeDepositBalancesUseCase;
import org.ikigaidigital.domain.interest.MonthlyInterestCalculator;
import org.ikigaidigital.domain.port.out.TimeDepositRepository;

public final class UpdateTimeDepositBalancesService implements UpdateTimeDepositBalancesUseCase {
    private final TimeDepositRepository repository;
    private final MonthlyInterestCalculator calculator;

    public UpdateTimeDepositBalancesService(TimeDepositRepository repository, MonthlyInterestCalculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    @Override
    public void updateBalances() {
        var updated = repository.findAllForBalanceUpdate().stream().map(calculator::applyTo).toList();
        repository.saveBalances(updated);
    }
}
