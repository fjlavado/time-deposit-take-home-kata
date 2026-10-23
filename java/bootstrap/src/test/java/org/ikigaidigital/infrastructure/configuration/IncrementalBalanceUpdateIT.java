package org.ikigaidigital.infrastructure.configuration;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.ikigaidigital.application.port.in.UpdateDepositBalancesUseCase;
import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;
import org.ikigaidigital.domain.port.out.TimeDepositRepositoryPort;
import org.ikigaidigital.infrastructure.persistence.repository.TimeDepositJdbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
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

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.connection-timeout=3000"
})
@Testcontainers
@Import(IncrementalBalanceUpdateIT.ProbeConfiguration.class)
class IncrementalBalanceUpdateIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");
    private static final BalanceUpdateJdbcProbe JDBC_PROBE = new BalanceUpdateJdbcProbe();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired UpdateDepositBalancesUseCase useCase;
    @Autowired ObservedRepository observed;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Environment environment;
    @Autowired TimeDepositJdbcRepository aggregates;

    @BeforeEach
    void reset() {
        assertThat(JDBC_PROBE.openResults).hasValue(0);
        assertThat(JDBC_PROBE.openStatements).hasValue(0);
        observed.reset();
        jdbc.execute("TRUNCATE withdrawals, \"timeDeposits\" RESTART IDENTITY");
        JDBC_PROBE.reset();
    }

    @Test
    void calculatesOnlyNewMonthlyPeriodsForEveryPlanAndEligibilityBoundary() {
        jdbc.execute("""
                INSERT INTO "timeDeposits" (id, "planType", balance, days, last_processed_days)
                VALUES (1, 'basic', 1000, 30, 0), (2, 'basic', 1000, 31, 0),
                       (3, 'premium', 1000, 45, 0), (4, 'premium', 1000, 46, 0),
                       (5, 'student', 1000, 365, 331), (6, 'student', 1000, 366, 365),
                       (7, 'unknown', 1000, 400, 0)
                """);
        jdbc.execute("INSERT INTO withdrawals (\"timeDepositId\", amount, date) VALUES (2, 12.34, DATE '2026-01-02')");
        var withdrawals = jdbc.queryForList("SELECT * FROM withdrawals");

        useCase.updateBalances();
        assertThat(balances()).containsExactly(1000.0, 1000.83, 1000.0, 1004.17, 1002.5, 1000.0, 1000.0);
        jdbc.execute("UPDATE \"timeDeposits\" SET days = 60, version = version + 1 WHERE id = 2");
        useCase.updateBalances();
        assertThat(balance(2)).isEqualTo(1000.83);

        jdbc.execute("UPDATE \"timeDeposits\" SET days = 91, version = version + 1 WHERE id = 2");
        jdbc.execute("UPDATE \"timeDeposits\" SET days = 106, version = version + 1 WHERE id = 4");
        jdbc.execute("UPDATE \"timeDeposits\" SET days = 400, version = version + 1 WHERE id IN (5, 6)");
        useCase.updateBalances();

        assertThat(balance(2)).isCloseTo(1002.49, within(1e-9));
        assertThat(balance(4)).isCloseTo(1012.55, within(1e-9));
        assertThat(balance(5)).isEqualTo(1002.5);
        assertThat(balance(6)).isEqualTo(1000.0);
        assertThat(jdbc.queryForList("SELECT * FROM withdrawals")).isEqualTo(withdrawals);
        assertReleased();
    }

    @Test
    void catchesUpAnExpiredStudentsEarlierPeriodsButNeverCreditsAfterOneYear() {
        jdbc.execute("INSERT INTO \"timeDeposits\" (id, \"planType\", balance, days) VALUES (1, 'student', 1000, 400)");
        useCase.updateBalances();
        assertThat(balance(1)).isCloseTo(1030.42, within(1e-9));
        assertThat(jdbc.queryForObject("SELECT last_processed_days FROM \"timeDeposits\"", Integer.class)).isEqualTo(400);
        assertReleased();
    }

    @Test
    void secondInvocationChangesNeitherBalancesNorWatermarksNorVersions() {
        insertBasic(205);
        useCase.updateBalances();
        var before = jdbc.queryForList("SELECT * FROM \"timeDeposits\" ORDER BY id");
        JDBC_PROBE.reset();

        useCase.updateBalances();

        assertThat(jdbc.queryForList("SELECT * FROM \"timeDeposits\" ORDER BY id")).isEqualTo(before);
        assertThat(JDBC_PROBE.selects).hasValue(1);
        assertThat(JDBC_PROBE.batchSizes).isEmpty();
        assertThat(JDBC_PROBE.closedResults).hasValue(1);
        assertReleased();
    }

    @Test
    void streamsTenThousandRowsWithOneCursorAndRealBoundedDriverBatches() {
        insertBasic(10005);
        jdbc.execute("""
                INSERT INTO withdrawals ("timeDepositId", amount, date)
                SELECT id, 1.00, DATE '2026-01-02' FROM "timeDeposits"
                """);
        observed.onFirstBatch = () -> assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_cursors
                WHERE position('WHERE days > last_processed_days' in statement) > 0
                  AND statement NOT LIKE '%pg_cursors%'
                """, Integer.class)).isEqualTo(1);

        useCase.updateBalances();

        assertThat(JDBC_PROBE.selects).hasValue(1);
        assertThat(JDBC_PROBE.fetchSizes).containsExactly(100);
        assertThat(JDBC_PROBE.autoCommit).containsExactly(false);
        assertThat(JDBC_PROBE.batchSizes).hasSize(101);
        assertThat(JDBC_PROBE.batchSizes.subList(0, 100)).containsOnly(100);
        assertThat(JDBC_PROBE.batchSizes.getLast()).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM "timeDeposits"
                WHERE balance = 1000.83 AND last_processed_days = 31 AND version = 1
                """, Integer.class)).isEqualTo(10005);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM withdrawals", Integer.class)).isEqualTo(10005);
        assertThat(JDBC_PROBE.closedResults).hasValue(1);
        assertReleased();
    }

    @Test
    void closesTheRealCursorAndReleasesThePoolConnectionAfterRepeatedSuccesses() {
        insertBasic(1);
        for (int run = 0; run < 8; run++) {
            useCase.updateBalances();
            assertReleased();
        }
        assertThat(JDBC_PROBE.closedResults).hasValue(8);
        assertThat(observed.closedDuringTransaction).hasValue(8);
    }

    @Test
    void closesTheRealCursorAndRollsBackEarlierBatchesWhenReadingFails() {
        insertBasic(205);
        observed.failOnRead = true;
        for (int run = 0; run < 4; run++) {
            assertThatThrownBy(useCase::updateBalances).isInstanceOf(IllegalStateException.class).hasMessage("read failure");
            assertUnchanged();
            assertReleased();
        }
        assertThat(JDBC_PROBE.closedResults).hasValue(4);
        assertThat(observed.closedDuringTransaction).hasValue(4);
        observed.failOnRead = false;
        useCase.updateBalances();
        assertThat(balance(205)).isEqualTo(1000.83);
        assertReleased();
    }

    @Test
    void closesTheRealCursorAndRollsBackEveryBatchWhenWritingFails() {
        insertBasic(205);
        observed.failOnWrite = true;
        assertThatThrownBy(useCase::updateBalances).isInstanceOf(IllegalStateException.class).hasMessage("write failure");
        assertUnchanged();
        assertThat(JDBC_PROBE.closedResults).hasValue(1);
        assertThat(observed.closedDuringTransaction).hasValue(1);
        assertReleased();
    }

    @Test
    void optimisticConflictRollsBackOtherSuccessfulStatementsInTheSameBatch() {
        insertBasic(2);
        var transaction = new TransactionTemplate(transactions);
        var snapshots = transaction.execute(status -> {
            try (var stream = observed.streamPending()) {
                return stream.toList();
            }
        });
        snapshots.forEach(deposit -> {
            deposit.setBalance(9999.0);
            deposit.markCalculationProcessed();
        });
        jdbc.execute("UPDATE \"timeDeposits\" SET version = version + 1 WHERE id = 2");

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> observed.saveBatch(snapshots)))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(balances()).containsExactly(1000.0, 1000.0);
        assertThat(jdbc.queryForList("SELECT version FROM \"timeDeposits\" ORDER BY id", Long.class)).containsExactly(0L, 1L);
        assertReleased();
    }

    @Test
    void savesBatchWithoutRoundingBalancesOrOverwritingMetadataAndWithdrawals() {
        jdbc.execute("""
                INSERT INTO "timeDeposits" (id, "planType", balance, days, last_processed_days, version)
                VALUES (1, 'basic', 12.345, 31, 10, 7)
                """);
        jdbc.execute("INSERT INTO withdrawals (id, \"timeDepositId\", amount, date) VALUES (11, 1, 2.50, DATE '2026-01-02')");
        double balance = 12.345 + 0.01;

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            try (var stream = observed.streamPending()) {
                var snapshot = stream.findFirst().orElseThrow();
                // Different plan, age, and withdrawals must not overwrite stored metadata.
                observed.saveBatch(List.of(new TimeDeposit(snapshot.getId(), PlanType.PREMIUM, balance, 99,
                        List.of(), 31, snapshot.getVersion())));
            }
        });

        var actual = aggregates.findById(1).orElseThrow();
        assertThat(actual.planType()).isEqualTo("basic");
        assertThat(actual.days()).isEqualTo(31);
        assertThat(actual.lastProcessedDays()).isEqualTo(31);
        assertThat(actual.version()).isEqualTo(8L);
        assertThat(actual.withdrawals()).singleElement().satisfies(withdrawal -> {
            assertThat(withdrawal.id()).isEqualTo(11);
            assertThat(withdrawal.amount()).isEqualByComparingTo("2.50");
            assertThat(withdrawal.date()).isEqualTo(LocalDate.of(2026, 1, 2));
        });
        assertThat(balance(1)).isEqualTo(balance);
        assertThat(jdbc.queryForObject("SELECT balance FROM \"timeDeposits\" WHERE id = 1", BigDecimal.class))
                .isEqualByComparingTo(BigDecimal.valueOf(balance));
        assertReleased();
    }

    @Test
    void springDataAggregateSavesAlsoRespectTheSameVersionColumn() {
        insertBasic(1);
        var stale = aggregates.findById(1).orElseThrow();
        useCase.updateBalances();

        assertThatThrownBy(() -> aggregates.save(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(balance(1)).isEqualTo(1000.83);
        assertReleased();
    }

    @Test
    void concurrentHttpTriggersReturnOneSuccessAndOneConflictThenRetryIsANoop() throws Exception {
        insertBasic(205);
        observed.beforeFirstSave = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(this::postUpdate);
            var second = executor.submit(this::postUpdate);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 409);
        } finally {
            observed.beforeFirstSave = null;
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"timeDeposits\" WHERE version = 1 AND balance = 1000.83", Integer.class))
                .isEqualTo(205);
        assertThat(JDBC_PROBE.closedResults).hasValue(2);
        assertReleased();
        var before = jdbc.queryForList("SELECT * FROM \"timeDeposits\" ORDER BY id");
        assertThat(postUpdate()).isEqualTo(204);
        assertThat(jdbc.queryForList("SELECT * FROM \"timeDeposits\" ORDER BY id")).isEqualTo(before);
        assertReleased();
    }

    @Test
    void upgradesPopulatedV1WithoutReplacingWithdrawalsOrLosingData() {
        var v1 = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("upgrade_fixture").target("1").load();
        try {
            v1.migrate();
            jdbc.execute("INSERT INTO upgrade_fixture.\"timeDeposits\" (id, \"planType\", balance, days) VALUES (1, 'basic', 123.45, 31)");
            jdbc.execute("INSERT INTO upgrade_fixture.withdrawals (\"timeDepositId\", amount, date) VALUES (1, 12.34, DATE '2026-01-02')");
            var latest = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas("upgrade_fixture").load();
            assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
            latest.validate();
            assertThat(latest.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForObject("SELECT balance FROM upgrade_fixture.\"timeDeposits\"", BigDecimal.class))
                    .isEqualByComparingTo("123.45");
            assertThat(jdbc.queryForObject("SELECT last_processed_days FROM upgrade_fixture.\"timeDeposits\"", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT version FROM upgrade_fixture.\"timeDeposits\"", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM upgrade_fixture.withdrawals", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname = 'upgrade_fixture' AND indexname = 'withdrawals_time_deposit_id_idx'", Integer.class))
                    .isEqualTo(1);
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS upgrade_fixture CASCADE");
        }
    }

    private void insertBasic(int count) {
        jdbc.update("""
                INSERT INTO "timeDeposits" (id, "planType", balance, days)
                SELECT id, 'basic', 1000, 31 FROM generate_series(1, ?) AS id
                """, count);
    }

    private List<Double> balances() {
        return jdbc.queryForList("SELECT balance FROM \"timeDeposits\" ORDER BY id", Double.class);
    }

    private double balance(int id) {
        return jdbc.queryForObject("SELECT balance FROM \"timeDeposits\" WHERE id = ?", Double.class, id);
    }

    private void assertUnchanged() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"timeDeposits\" WHERE balance <> 1000 OR last_processed_days <> 0 OR version <> 0", Integer.class))
                .isZero();
    }

    private void assertReleased() {
        assertThat(JDBC_PROBE.openResults).hasValue(0);
        assertThat(JDBC_PROBE.openStatements).hasValue(0);
        assertThat(JDBC_PROBE.autoCommit).containsOnly(false);
        try {
            assertThat(dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections()).isZero();
        } catch (java.sql.SQLException exception) {
            throw new AssertionError(exception);
        }
    }

    private int postUpdate() throws Exception {
        var uri = URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + "/time-deposits/balance-updates");
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).POST(HttpRequest.BodyPublishers.noBody()).build();
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.body()).isEmpty();
            return response.statusCode();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        static BeanPostProcessor jdbcProbe() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    return bean instanceof DataSource source ? JDBC_PROBE.wrap(source) : bean;
                }
            };
        }

        @Bean
        @Primary
        ObservedRepository observedRepository(@Qualifier("balanceUpdateRepository") TimeDepositRepositoryPort delegate) {
            return new ObservedRepository(delegate);
        }
    }

    static final class ObservedRepository implements TimeDepositRepositoryPort {
        private final TimeDepositRepositoryPort delegate;
        final AtomicInteger closedDuringTransaction = new AtomicInteger();
        volatile boolean failOnRead;
        volatile boolean failOnWrite;
        volatile CyclicBarrier beforeFirstSave;
        volatile Runnable onFirstBatch;

        ObservedRepository(TimeDepositRepositoryPort delegate) { this.delegate = delegate; }

        void reset() {
            closedDuringTransaction.set(0);
            failOnRead = false;
            failOnWrite = false;
            beforeFirstSave = null;
            onFirstBatch = null;
        }

        @Override
        public Stream<TimeDeposit> streamPending() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return delegate.streamPending().peek(deposit -> {
                if (failOnRead && deposit.getId() == 101) {
                    throw new IllegalStateException("read failure");
                }
            }).onClose(() -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                closedDuringTransaction.incrementAndGet();
            });
        }

        @Override
        public void saveBatch(List<TimeDeposit> deposits) {
            var hook = onFirstBatch;
            if (hook != null && deposits.getFirst().getId() == 1) {
                hook.run();
            }
            var barrier = beforeFirstSave;
            if (barrier != null && deposits.getFirst().getId() == 1) {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException("Concurrent requests did not reach the write barrier", exception);
                }
            }
            delegate.saveBatch(deposits);
            if (failOnWrite && deposits.getFirst().getId() > 100) {
                throw new IllegalStateException("write failure");
            }
        }
    }
}
