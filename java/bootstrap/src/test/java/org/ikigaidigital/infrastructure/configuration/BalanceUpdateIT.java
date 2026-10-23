package org.ikigaidigital.infrastructure.configuration;

import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.services.TimeDepositCalculator;
import org.ikigaidigital.application.bus.DefaultCommandBus;
import org.ikigaidigital.application.command.UpdateTimeDepositBalancesCommand;
import org.ikigaidigital.application.command.handler.UpdateTimeDepositBalancesCommandHandler;
import org.ikigaidigital.application.port.in.CommandBus;
import org.ikigaidigital.application.port.in.QueryBus;
import org.ikigaidigital.application.query.Query;
import org.ikigaidigital.application.query.RetrieveTimeDepositsQuery;
import org.ikigaidigital.application.query.handler.QueryHandler;
import org.ikigaidigital.domain.port.out.TimeDepositRepository;
import org.ikigaidigital.domain.port.out.TimeDepositRepositoryPort;
import org.ikigaidigital.application.service.UpdateDepositBalancesService;
import org.ikigaidigital.domain.model.PageRequestVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.dao.OptimisticLockingFailureException;
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
import java.util.stream.Stream;
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
    @Autowired TimeDepositRepositoryPort balanceRepository;
    @Autowired QueryBus queries;
    @Autowired CommandBus commands;
    @Autowired TimeDepositCalculator calculator;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcMappingContext mappingContext;

    @BeforeEach
    void resetData() {
        jdbc.execute("TRUNCATE withdrawals, \"timeDeposits\" RESTART IDENTITY");
        jdbc.execute("""
                INSERT INTO "timeDeposits" (id, "planType", balance, days, last_processed_days)
                VALUES (1, 'basic', 1000, 31, 0), (2, 'student', 1000, 365, 331),
                       (3, 'premium', 1000, 46, 0), (4, 'student', 1000, 366, 365)
                """);
        jdbc.execute("INSERT INTO withdrawals (\"timeDepositId\", amount, date) VALUES (1, 12.34, DATE '2026-01-02')");
    }

    @Test
    void bootMigratesDatabaseAndWiresTransactionalCommandDispatch() {
        assertThat(mappingContext.isSingleQueryLoadingEnabled()).isTrue();
        commands.dispatch(new UpdateTimeDepositBalancesCommand());

        assertThat(queries.dispatch(new RetrieveTimeDepositsQuery(0, 20)).content()).extracting(TimeDeposit::getBalance)
                .containsExactly(1000.83, 1002.5, 1004.17, 1000.0);
        assertThat(queries.dispatch(new RetrieveTimeDepositsQuery(0, 20)).content().getFirst().getWithdrawals()).hasSize(1);
    }

    @Test
    void rollsBackPartialWritesAndStartsTransactionBeforeLoading() {
        jdbc.execute("""
                INSERT INTO "timeDeposits" (id, "planType", balance, days)
                SELECT id, 'basic', 1000, 31 FROM generate_series(5, 105) AS id
                """);
        var failingRepository = new TimeDepositRepositoryPort() {
            @Override
            public Stream<Integer> streamPending() {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                return balanceRepository.streamPending();
            }

            @Override
            public List<TimeDeposit> findAllById(List<Integer> ids) {
                return balanceRepository.findAllById(ids);
            }

            @Override
            public void saveAll(List<TimeDeposit> deposits) {
                if (deposits.getFirst().getId() > 100) {
                    balanceRepository.saveAll(List.of(deposits.getFirst()));
                    throw new IllegalStateException("Simulated failure while persisting the second group");
                }
                balanceRepository.saveAll(deposits);
            }
        };
        var handler = new UpdateTimeDepositBalancesCommandHandler(new UpdateDepositBalancesService(failingRepository, calculator));
        var transactional = new TransactionalCommandBus(new DefaultCommandBus(List.of(handler)),
                new TransactionTemplate(transactions));

        assertThatThrownBy(() -> transactional.dispatch(new UpdateTimeDepositBalancesCommand()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"timeDeposits\" WHERE balance <> 1000", Integer.class))
                .isZero();
    }

    @Test
    void concurrentTriggersNeverCreditTheSamePeriodTwice() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return tryUpdate();
            });
            var second = executor.submit(() -> {
                start.await();
                return tryUpdate();
            });
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).contains(true);
        }

        assertThat(repository.search(new PageRequestVO(0, 20)).content().get(1).getBalance()).isEqualTo(1002.5);
    }

    private boolean tryUpdate() {
        try {
            commands.dispatch(new UpdateTimeDepositBalancesCommand());
            return true;
        } catch (OptimisticLockingFailureException conflict) {
            return false;
        }
    }

    @Test
    void persistsEveryDepositAcrossMultipleSaveGroupsExactlyOnce() {
        jdbc.execute("TRUNCATE withdrawals, \"timeDeposits\" RESTART IDENTITY");
        jdbc.execute("""
                INSERT INTO "timeDeposits" (id, "planType", balance, days)
                SELECT id, 'basic', 1000, 31 FROM generate_series(1, 205) AS id
                """);

        commands.dispatch(new UpdateTimeDepositBalancesCommand());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"timeDeposits\" WHERE balance = 1000.83", Integer.class))
                .isEqualTo(205);
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
