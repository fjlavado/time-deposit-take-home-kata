package org.ikigaidigital.application.job;

import org.ikigaidigital.application.port.in.BalanceUpdateJob;
import org.ikigaidigital.application.port.in.UpdateDepositBalancesUseCase;
import org.ikigaidigital.application.port.out.BalanceUpdateExecutionRepository;
import org.ikigaidigital.application.port.out.BackgroundTaskExecutor;

import java.time.Instant;
import java.util.UUID;

/** The database reservation covers all callers and application instances, including queued work. */
public final class BackgroundBalanceUpdateJob implements BalanceUpdateJob {
    private static final System.Logger LOG = System.getLogger(BackgroundBalanceUpdateJob.class.getName());
    private final BackgroundTaskExecutor executor;
    private final UpdateDepositBalancesUseCase updateBalances;
    private final BalanceUpdateExecutionRepository executions;

    public BackgroundBalanceUpdateJob(BackgroundTaskExecutor executor, UpdateDepositBalancesUseCase updateBalances,
                                      BalanceUpdateExecutionRepository executions) {
        this.executor = executor;
        this.updateBalances = updateBalances;
        this.executions = executions;
    }

    @Override
    public BalanceUpdateJobResult start() {
        var id = UUID.randomUUID();
        var reserved = executions.startOrGetActive(id, Instant.now());
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
            executions.update(id, BalanceUpdateExecutionStatus.RUNNING, null);
            // The injected transactional use case opens its transaction on this worker thread.
            updateBalances.updateBalances();
            outcome = BalanceUpdateExecutionStatus.COMPLETED;
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.ERROR,
                    "Balance update execution " + id + " failed; its balance transaction rolled back", failure);
        } finally {
            try {
                // COMPLETED is recorded only after commit; FAILED survives a balance rollback.
                finish(id, outcome);
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.ERROR,
                        "Unable to finalize balance update execution " + id + " as " + outcome, failure);
            }
        }
    }

    private void finish(UUID id, BalanceUpdateExecutionStatus status) {
        executions.update(id, status, Instant.now());
    }
}
