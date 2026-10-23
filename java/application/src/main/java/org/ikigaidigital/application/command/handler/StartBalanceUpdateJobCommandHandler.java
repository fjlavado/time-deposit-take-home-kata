package org.ikigaidigital.application.command.handler;

import org.ikigaidigital.application.command.StartBalanceUpdateJobCommand;
import org.ikigaidigital.application.job.BalanceUpdateJobResult;
import org.ikigaidigital.application.port.in.BalanceUpdateJob;

public final class StartBalanceUpdateJobCommandHandler
        implements CommandHandler<StartBalanceUpdateJobCommand, BalanceUpdateJobResult> {
    private final BalanceUpdateJob job;

    public StartBalanceUpdateJobCommandHandler(BalanceUpdateJob job) {
        this.job = job;
    }

    @Override
    public Class<StartBalanceUpdateJobCommand> commandType() {
        return StartBalanceUpdateJobCommand.class;
    }

    @Override
    public BalanceUpdateJobResult handle(StartBalanceUpdateJobCommand command) {
        return job.start();
    }
}
