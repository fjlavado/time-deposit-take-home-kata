package org.ikigaidigital.infrastructure.configuration;

import org.ikigaidigital.application.bus.DefaultCommandBus;
import org.ikigaidigital.application.command.UpdateTimeDepositBalancesCommand;
import org.ikigaidigital.application.command.handler.UpdateTimeDepositBalancesCommandHandler;
import org.ikigaidigital.application.port.in.CommandBus;
import org.ikigaidigital.application.port.in.QueryBus;
import org.ikigaidigital.application.query.Query;
import org.ikigaidigital.application.query.RetrieveTimeDepositsQuery;
import org.ikigaidigital.application.query.handler.QueryHandler;
import org.ikigaidigital.domain.interest.MonthlyInterestCalculator;
import org.ikigaidigital.domain.model.TimeDeposit;
import org.ikigaidigital.domain.port.out.TimeDepositRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Import(BalanceUpdateIT.QueryProbeConfiguration.class)
class BalanceUpdateIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired TimeDepositRepository repository;
    @Autowired QueryBus queries;
    @Autowired CommandBus commands;
    @Autowired MonthlyInterestCalculator calculator;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void resetData() {
        jdbc.execute("TRUNCATE withdrawals, \"timeDeposits\" RESTART IDENTITY");
        jdbc.execute("""
                INSERT INTO "timeDeposits" (id, "planType", balance, days)
                VALUES (1, 'basic', 1000, 31), (2, 'student', 1000, 365),
                       (3, 'premium', 1000, 46), (4, 'student', 1000, 366)
                """);
        jdbc.execute("INSERT INTO withdrawals (\"timeDepositId\", amount, date) VALUES (1, 12.34, DATE '2026-01-02')");
    }

    @Test
    void bootMigratesDatabaseAndWiresTransactionalCommandDispatch() {
        commands.dispatch(new UpdateTimeDepositBalancesCommand());

        assertThat(queries.dispatch(new RetrieveTimeDepositsQuery())).extracting(TimeDeposit::balance)
                .containsExactly(1000.83, 1002.5, 1004.17, 1000.0);
        assertThat(queries.dispatch(new RetrieveTimeDepositsQuery()).getFirst().withdrawals()).hasSize(1);
    }

    @Test
    void rollsBackPartialWritesAndStartsTransactionBeforeLoading() {
        var failingRepository = new TimeDepositRepository() {
            @Override
            public List<TimeDeposit> findAll() {
                return repository.findAll();
            }

            @Override
            public List<TimeDeposit> findAllForBalanceUpdate() {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                return repository.findAllForBalanceUpdate();
            }

            @Override
            public void saveBalances(List<TimeDeposit> deposits) {
                repository.saveBalances(List.of(deposits.getFirst()));
                throw new IllegalStateException("Simulated failure after the first persisted balance");
            }
        };
        var handler = new UpdateTimeDepositBalancesCommandHandler(failingRepository, calculator);
        var transactional = new TransactionalCommandBus(new DefaultCommandBus(List.of(handler)),
                new TransactionTemplate(transactions));

        assertThatThrownBy(() -> transactional.dispatch(new UpdateTimeDepositBalancesCommand()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(repository.findAll()).extracting(TimeDeposit::balance)
                .containsExactly(1000.0, 1000.0, 1000.0, 1000.0);
    }

    @Test
    void serializesConcurrentUpdatesWithoutLosingAnInterestCredit() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return commands.dispatch(new UpdateTimeDepositBalancesCommand());
            });
            var second = executor.submit(() -> {
                start.await();
                return commands.dispatch(new UpdateTimeDepositBalancesCommand());
            });
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }

        assertThat(repository.findAll().get(1).balance()).isEqualTo(1005.01);
    }

    @Test
    void queryDispatchDoesNotStartATransaction() {
        assertThat(queries.dispatch(new TransactionActiveQuery())).isFalse();
    }

    private record TransactionActiveQuery() implements Query<Boolean> {}

    @TestConfiguration(proxyBeanMethods = false)
    static class QueryProbeConfiguration {
        @Bean
        QueryHandler<TransactionActiveQuery, Boolean> transactionActiveQueryHandler() {
            return new QueryHandler<>() {
                @Override
                public Class<TransactionActiveQuery> queryType() {
                    return TransactionActiveQuery.class;
                }

                @Override
                public Boolean handle(TransactionActiveQuery query) {
                    return TransactionSynchronizationManager.isActualTransactionActive();
                }
            };
        }
    }
}
