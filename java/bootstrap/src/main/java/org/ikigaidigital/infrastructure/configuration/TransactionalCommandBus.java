package org.ikigaidigital.infrastructure.configuration;

import org.ikigaidigital.application.command.Command;
import org.ikigaidigital.application.port.in.CommandBus;
import org.springframework.transaction.support.TransactionTemplate;

/** The complete command, including aggregate loading and saving, participates in one transaction. */
public final class TransactionalCommandBus implements CommandBus {
    private final CommandBus delegate;
    private final TransactionTemplate transaction;

    public TransactionalCommandBus(CommandBus delegate, TransactionTemplate transaction) {
        this.delegate = delegate;
        this.transaction = transaction;
    }

    @Override
    public <R> R dispatch(Command<R> command) {
        return transaction.execute(status -> delegate.dispatch(command));
    }
}
