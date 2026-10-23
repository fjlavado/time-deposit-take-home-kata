package org.ikigaidigital.infrastructure.transaction;

import org.ikigaidigital.application.command.Command;
import org.ikigaidigital.application.command.StartBalanceUpdateJobCommand;
import org.ikigaidigital.application.port.in.CommandBus;
import org.springframework.transaction.support.TransactionTemplate;

/** Balance processing is transactional; job submission owns only its short audit transactions. */
public final class TransactionalCommandBus implements CommandBus {
    private final CommandBus delegate;
    private final TransactionTemplate transaction;

    public TransactionalCommandBus(CommandBus delegate, TransactionTemplate transaction) {
        this.delegate = delegate;
        this.transaction = transaction;
    }

    @Override
    public <R> R dispatch(Command<R> command) {
        if (command instanceof StartBalanceUpdateJobCommand) {
            // Never hold a request-thread connection while the job reserves its database slot.
            // The transactional use case opens the processing transaction on the worker thread.
            return delegate.dispatch(command);
        }
        return transaction.execute(status -> delegate.dispatch(command));
    }
}
