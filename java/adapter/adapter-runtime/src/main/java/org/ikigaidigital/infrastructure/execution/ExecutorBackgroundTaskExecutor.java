package org.ikigaidigital.infrastructure.execution;

import org.ikigaidigital.application.port.out.BackgroundTaskExecutor;

import java.util.concurrent.Executor;

public final class ExecutorBackgroundTaskExecutor implements BackgroundTaskExecutor {
    private final Executor executor;

    public ExecutorBackgroundTaskExecutor(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void execute(Runnable task) {
        executor.execute(task);
    }
}
