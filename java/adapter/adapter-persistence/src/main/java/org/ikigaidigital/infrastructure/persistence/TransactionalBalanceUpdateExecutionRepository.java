package org.ikigaidigital.infrastructure.persistence;

import org.ikigaidigital.application.job.BalanceUpdateExecutionStatus;
import org.ikigaidigital.application.port.out.BalanceUpdateExecutionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

/** Admission commits before submission; audit transitions survive a balance rollback. */
public final class TransactionalBalanceUpdateExecutionRepository implements BalanceUpdateExecutionRepository {
    private final BalanceUpdateExecutionRepository delegate;
    private final TransactionTemplate transactions;

    public TransactionalBalanceUpdateExecutionRepository(BalanceUpdateExecutionRepository delegate,
                                                         PlatformTransactionManager manager) {
        this.delegate = delegate;
        this.transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // Reservation retries need a fresh snapshot after a conflicting execution finishes.
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public UUID startOrGetActive(UUID proposedId, Instant startedAt) {
        return transactions.execute(tx -> delegate.startOrGetActive(proposedId, startedAt));
    }

    @Override
    public void update(UUID id, BalanceUpdateExecutionStatus status, Instant finishedAt) {
        transactions.executeWithoutResult(tx -> delegate.update(id, status, finishedAt));
    }
}
