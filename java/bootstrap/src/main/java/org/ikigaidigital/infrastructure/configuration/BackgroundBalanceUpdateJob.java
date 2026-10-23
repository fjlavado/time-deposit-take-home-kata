package org.ikigaidigital.infrastructure.configuration;

import org.ikigaidigital.application.job.BalanceUpdateJobResult;
import org.ikigaidigital.application.port.in.BalanceUpdateJob;
import org.ikigaidigital.application.port.in.UpdateDepositBalancesUseCase;
import org.ikigaidigital.domain.model.BalanceUpdateExecutionStatus;
import org.ikigaidigital.domain.port.out.BalanceUpdateExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;

/** The database reservation covers all callers and application instances, including queued work. */
public final class BackgroundBalanceUpdateJob implements BalanceUpdateJob {
    private static final Logger LOG = LoggerFactory.getLogger(BackgroundBalanceUpdateJob.class);
    private final Executor executor;
    private final UpdateDepositBalancesUseCase updateBalances;
    private final BalanceUpdateExecutionRepository executions;
    private final TransactionTemplate logTransactions;

    public BackgroundBalanceUpdateJob(Executor executor, UpdateDepositBalancesUseCase updateBalances,
                                      BalanceUpdateExecutionRepository executions, TransactionTemplate logTransactions) {
        this.executor = executor;
        this.updateBalances = updateBalances;
        this.executions = executions;
        this.logTransactions = logTransactions;
    }

    @Override
    public BalanceUpdateJobResult start() {
        var id = UUID.randomUUID();
        var reserved = logTransactions.execute(tx -> executions.startOrGetActive(id, Instant.now()));
        if (!id.equals(reserved)) {
            return new BalanceUpdateJobResult(reserved, BalanceUpdateJobResult.Status.RUNNING);
        }
        try {
            executor.execute(() -> run(id));
            return new BalanceUpdateJobResult(id, BalanceUpdateJobResult.Status.STARTED);
        } catch (RuntimeException failure) {
            finish(id, BalanceUpdateExecutionStatus.FAILED);
            throw failure;
        }
    }

    private void run(UUID id) {
        var outcome = BalanceUpdateExecutionStatus.FAILED;
        try {
            logTransactions.executeWithoutResult(tx -> executions.update(id, BalanceUpdateExecutionStatus.RUNNING, null));
            // The injected transactional use case opens its transaction on this worker thread.
            updateBalances.updateBalances();
            outcome = BalanceUpdateExecutionStatus.COMPLETED;
        } catch (RuntimeException failure) {
            LOG.error("Balance update execution {} failed; its balance transaction rolled back", id, failure);
        } finally {
            try {
                // COMPLETED is recorded only after commit; FAILED survives a balance rollback.
                finish(id, outcome);
            } catch (RuntimeException failure) {
                LOG.error("Unable to finalize balance update execution {} as {}", id, outcome, failure);
            }
        }
    }

    private void finish(UUID id, BalanceUpdateExecutionStatus status) {
        logTransactions.executeWithoutResult(tx -> executions.update(id, status, Instant.now()));
    }
}
