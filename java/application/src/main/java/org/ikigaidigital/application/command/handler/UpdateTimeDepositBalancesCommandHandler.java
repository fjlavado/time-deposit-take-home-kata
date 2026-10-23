package org.ikigaidigital.application.command.handler;

import org.ikigaidigital.application.command.UpdateTimeDepositBalancesCommand;
import org.ikigaidigital.services.TimeDepositCalculator;
import org.ikigaidigital.domain.model.PageRequest;
import org.ikigaidigital.domain.port.out.TimeDepositRepository;

public final class UpdateTimeDepositBalancesCommandHandler
        implements CommandHandler<UpdateTimeDepositBalancesCommand, Void> {
    private static final int BATCH_SIZE = 100;
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
        repository.lockForBalanceUpdate();
        // ID ordering is stable because interest changes only balances; locks span every page.
        for (int pageNumber = 0; ; pageNumber++) {
            var page = repository.search(new PageRequest(pageNumber, BATCH_SIZE));
            calculator.updateBalance(page.content());
            repository.saveBalances(page.content());
            if (!page.hasNext()) {
                break;
            }
        }
        return null;
    }
}
