package org.ikigaidigital.infrastructure.configuration;

import org.ikigaidigital.application.command.StartBalanceUpdateJobCommand;
import org.ikigaidigital.application.command.UpdateTimeDepositBalancesCommand;
import org.ikigaidigital.application.job.BalanceUpdateJobResult;
import org.ikigaidigital.application.port.in.CommandBus;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TransactionalCommandBusTest {
    @Test
    void submissionDoesNotHoldAnOuterConnectionWhileReservingTheDatabaseSlot() {
        var delegate = mock(CommandBus.class);
        var transactions = mock(PlatformTransactionManager.class);
        var result = new BalanceUpdateJobResult(UUID.randomUUID(), BalanceUpdateJobResult.Status.STARTED);
        var command = new StartBalanceUpdateJobCommand();
        when(delegate.dispatch(command)).thenReturn(result);
        var bus = new TransactionalCommandBus(delegate, new TransactionTemplate(transactions));

        assertThat(bus.dispatch(command)).isEqualTo(result);

        verifyNoInteractions(transactions);
    }

    @Test
    void directProcessingCommandsStillCommitTheirTransaction() {
        var delegate = mock(CommandBus.class);
        var transactions = mock(PlatformTransactionManager.class);
        var transaction = new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(transaction);
        var command = new UpdateTimeDepositBalancesCommand();
        var bus = new TransactionalCommandBus(delegate, new TransactionTemplate(transactions));

        bus.dispatch(command);

        verify(delegate).dispatch(command);
        verify(transactions).commit(transaction);
    }
}
