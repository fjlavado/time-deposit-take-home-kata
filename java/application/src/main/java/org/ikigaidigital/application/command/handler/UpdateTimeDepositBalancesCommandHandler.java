package org.ikigaidigital.application.command.handler;

import org.ikigaidigital.application.command.UpdateTimeDepositBalancesCommand;
import org.ikigaidigital.domain.interest.MonthlyInterestCalculator;
import org.ikigaidigital.domain.port.out.TimeDepositRepository;

public final class UpdateTimeDepositBalancesCommandHandler
        implements CommandHandler<UpdateTimeDepositBalancesCommand, Void> {
    private final TimeDepositRepository repository;
    private final MonthlyInterestCalculator calculator;

    public UpdateTimeDepositBalancesCommandHandler(TimeDepositRepository repository, MonthlyInterestCalculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    @Override
    public Class<UpdateTimeDepositBalancesCommand> commandType() {
        return UpdateTimeDepositBalancesCommand.class;
    }

    @Override
    public Void handle(UpdateTimeDepositBalancesCommand command) {
        var updated = repository.findAllForBalanceUpdate().stream().map(calculator::applyTo).toList();
        repository.saveBalances(updated);
        return null;
    }
}
