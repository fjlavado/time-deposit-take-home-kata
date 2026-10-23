package org.ikigaidigital.application.port.out;

/** Submit work for background execution; rejection must be reported synchronously. */
@FunctionalInterface
public interface BackgroundTaskExecutor {
    void execute(Runnable task);
}
