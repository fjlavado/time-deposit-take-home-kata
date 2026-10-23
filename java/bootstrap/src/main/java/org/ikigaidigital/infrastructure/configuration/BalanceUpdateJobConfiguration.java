package org.ikigaidigital.infrastructure.configuration;

import org.ikigaidigital.application.command.handler.StartBalanceUpdateJobCommandHandler;
import org.ikigaidigital.application.job.BackgroundBalanceUpdateJob;
import org.ikigaidigital.application.port.in.BalanceUpdateJob;
import org.ikigaidigital.application.port.in.UpdateDepositBalancesUseCase;
import org.ikigaidigital.application.port.out.BalanceUpdateExecutionRepository;
import org.ikigaidigital.application.port.out.BackgroundTaskExecutor;
import org.ikigaidigital.infrastructure.execution.ExecutorBackgroundTaskExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
public class BalanceUpdateJobConfiguration {
    @Bean(destroyMethod = "close")
    @DependsOn("dataSource") // Drain accepted work before the connection pool is destroyed.
    ExecutorService balanceUpdateExecutor() {
        return Executors.newSingleThreadExecutor(Thread.ofPlatform().name("balance-update-", 0).factory());
    }

    @Bean
    BackgroundTaskExecutor backgroundTaskExecutor(ExecutorService balanceUpdateExecutor) {
        return new ExecutorBackgroundTaskExecutor(balanceUpdateExecutor);
    }

    @Bean
    BalanceUpdateJob balanceUpdateJob(BackgroundTaskExecutor executor, UpdateDepositBalancesUseCase balances,
                                      BalanceUpdateExecutionRepository executions) {
        return new BackgroundBalanceUpdateJob(executor, balances, executions);
    }

    @Bean
    StartBalanceUpdateJobCommandHandler startBalanceUpdateJobCommandHandler(BalanceUpdateJob job) {
        return new StartBalanceUpdateJobCommandHandler(job);
    }
}
