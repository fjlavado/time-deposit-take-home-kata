package org.ikigaidigital.infrastructure.configuration;

import org.ikigaidigital.application.command.handler.StartBalanceUpdateJobCommandHandler;
import org.ikigaidigital.application.port.in.BalanceUpdateJob;
import org.ikigaidigital.application.port.in.UpdateDepositBalancesUseCase;
import org.ikigaidigital.application.port.out.BalanceUpdateExecutionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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
    BalanceUpdateJob balanceUpdateJob(ExecutorService balanceUpdateExecutor, UpdateDepositBalancesUseCase balances,
                                      BalanceUpdateExecutionRepository executions, PlatformTransactionManager transactions) {
        var logTransactions = new TransactionTemplate(transactions);
        logTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        logTransactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return new BackgroundBalanceUpdateJob(balanceUpdateExecutor, balances, executions, logTransactions);
    }

    @Bean
    StartBalanceUpdateJobCommandHandler startBalanceUpdateJobCommandHandler(BalanceUpdateJob job) {
        return new StartBalanceUpdateJobCommandHandler(job);
    }
}
