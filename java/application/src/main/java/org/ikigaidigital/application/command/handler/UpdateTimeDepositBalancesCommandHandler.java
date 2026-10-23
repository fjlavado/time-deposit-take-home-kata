package org.ikigaidigital.application.command.handler;

import org.ikigaidigital.application.command.UpdateTimeDepositBalancesCommand;
import org.ikigaidigital.application.port.in.UpdateDepositBalancesUseCase;

public final class UpdateTimeDepositBalancesCommandHandler
        implements CommandHandler<UpdateTimeDepositBalancesCommand, Void> {
    private final UpdateDepositBalancesUseCase useCase;

    public UpdateTimeDepositBalancesCommandHandler(UpdateDepositBalancesUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public Class<UpdateTimeDepositBalancesCommand> commandType() {
        return UpdateTimeDepositBalancesCommand.class;
    }

    @Override
    public Void handle(UpdateTimeDepositBalancesCommand command) {
        useCase.updateBalances();
        return null;
    }
}
