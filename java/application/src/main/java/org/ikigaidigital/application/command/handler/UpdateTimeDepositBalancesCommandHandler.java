package org.ikigaidigital.application.command.handler;

import org.ikigaidigital.application.command.UpdateTimeDepositBalancesCommand;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.domain.port.out.TimeDepositRepository;

public final class UpdateTimeDepositBalancesCommandHandler
        implements CommandHandler<UpdateTimeDepositBalancesCommand, Void> {
    private final TimeDepositRepository repository;
    private final TimeDepositCalculator calculator;

    public UpdateTimeDepositBalancesCommandHandler(TimeDepositRepository repository, TimeDepositCalculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    @Override
    public Class<UpdateTimeDepositBalancesCommand> commandType() {
        return UpdateTimeDepositBalancesCommand.class;
    }

    @Override
    public Void handle(UpdateTimeDepositBalancesCommand command) {
        var deposits = repository.findAllForBalanceUpdate();
        calculator.updateBalance(deposits);
        repository.saveBalances(deposits);
        return null;
    }
}
