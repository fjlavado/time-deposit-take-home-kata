package org.ikigaidigital.infrastructure.configuration;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.ikigaidigital.application.port.in.UpdateDepositBalancesUseCase;
import org.ikigaidigital.domain.interest.TimeDeposit;
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
import java.util.concurrent.CopyOnWriteArrayList;
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
    void loadsPendingAggregatesWithTheirOwnWithdrawalsInIdOrder() {
        jdbc.execute("""
                INSERT INTO "timeDeposits" (id, "planType", balance, days, last_processed_days)
                VALUES (3, 'premium', 3000, 46, 0), (1, 'basic', 1000, 31, 0),
                       (2, 'student', 2000, 31, 31)
                """);
        jdbc.execute("""
                INSERT INTO withdrawals (id, "timeDepositId", amount, date)
                VALUES (12, 1, 0.10, DATE '2026-01-03'), (11, 1, 12.3456, DATE '2026-01-02'),
                       (21, 2, 9.00, DATE '2026-01-04'), (31, 3, 7.89, DATE '2026-01-05')
                """);

        var deposits = loadPendingSnapshots();

        assertThat(deposits).extracting(TimeDeposit::getId).containsExactly(1, 3);
        assertThat(deposits.getFirst().getWithdrawals()).extracting(withdrawal -> withdrawal.id())
                .containsExactly(11, 12);
        assertThat(deposits.getFirst().getWithdrawals().getFirst().amount()).isEqualByComparingTo("12.3456");
        assertThat(deposits.getLast().getWithdrawals()).singleElement().satisfies(withdrawal -> {
            assertThat(withdrawal.id()).isEqualTo(31);
            assertThat(withdrawal.amount()).isEqualByComparingTo("7.89");
            assertThat(withdrawal.date()).isEqualTo(LocalDate.of(2026, 1, 5));
        });
        assertReleased();
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
        insertWithdrawals();
        useCase.updateBalances();
        var before = jdbc.queryForList("SELECT * FROM \"timeDeposits\" ORDER BY id");
        var withdrawals = jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id");
        observed.reset();
        JDBC_PROBE.reset();

        useCase.updateBalances();

        assertThat(jdbc.queryForList("SELECT * FROM \"timeDeposits\" ORDER BY id")).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id")).isEqualTo(withdrawals);
        assertThat(observed.savedGroupSizes).isEmpty();
        assertThat(observed.loadedGroupSizes).isEmpty();
        assertThat(JDBC_PROBE.pendingReads).hasValue(1);
        assertThat(JDBC_PROBE.closedResults).hasValue(1);
        assertReleased();
    }

    @Test
    void streamsOnlyIdsAndLoadsAndSavesCompleteAggregatesInBoundedGroups() {
        insertBasic(10005);
        insertWithdrawals();
        var withdrawals = jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id");
        JDBC_PROBE.reset();
        observed.onFirstLoad = () -> {
            assertThat(JDBC_PROBE.cursorRows).hasValue(100);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM pg_cursors
                    WHERE position('SELECT id FROM "timeDeposits"' in statement) > 0
                      AND statement NOT LIKE '%pg_cursors%'
                    """, Integer.class)).isEqualTo(1);
        };

        useCase.updateBalances();

        assertThat(JDBC_PROBE.pendingReads).hasValue(1);
        assertThat(JDBC_PROBE.aggregateReads).hasValue(101);
        assertThat(JDBC_PROBE.fetchSizes).containsExactly(100);
        assertThat(JDBC_PROBE.autoCommit).containsExactly(false);
        assertThat(JDBC_PROBE.cursorRows).hasValue(10005);
        assertThat(JDBC_PROBE.closedResults).hasValue(1);
        assertThat(observed.savedGroupSizes).hasSize(101);
        assertThat(observed.savedGroupSizes.subList(0, 100)).containsOnly(100);
        assertThat(observed.savedGroupSizes.getLast()).isEqualTo(5);
        assertThat(observed.loadedGroupSizes).isEqualTo(observed.savedGroupSizes);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM "timeDeposits"
                WHERE balance = 1000.83 AND last_processed_days = 31 AND version = 1
                """, Integer.class)).isEqualTo(10005);
        assertThat(jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id")).isEqualTo(withdrawals);
        assertReleased();
    }

    @Test
    void releasesThePoolConnectionAfterRepeatedSuccesses() {
        insertBasic(1);
        for (int run = 0; run < 8; run++) {
            useCase.updateBalances();
            assertReleased();
        }
        assertThat(observed.savedGroupSizes).containsExactly(1);
        assertThat(JDBC_PROBE.closedResults).hasValue(8);
        assertThat(observed.closedDuringTransaction).hasValue(8);
    }

    @Test
    void rollsBackEveryAggregateAndItsWithdrawalsWhenALaterSaveGroupFails() {
        insertBasic(205);
        insertWithdrawals();
        var withdrawals = jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id");
        observed.failOnWrite = true;
        assertThatThrownBy(useCase::updateBalances).isInstanceOf(IllegalStateException.class).hasMessage("write failure");
        assertUnchanged();
        assertThat(observed.savedGroupSizes).containsExactly(100, 100);
        assertThat(JDBC_PROBE.closedResults).hasValue(1);
        assertThat(observed.closedDuringTransaction).hasValue(1);
        assertThat(jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id")).isEqualTo(withdrawals);
        assertReleased();
    }

    @Test
    void closesTheIdCursorAndRollsBackEarlierSavesWhenReadingIdsFails() {
        insertBasic(205);
        insertWithdrawals();
        var withdrawals = jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id");
        observed.failOnRead = true;

        for (int attempt = 0; attempt < 4; attempt++) {
            assertThatThrownBy(useCase::updateBalances)
                    .isInstanceOf(IllegalStateException.class).hasMessage("read failure");
            assertUnchanged();
            assertReleased();
        }
        assertThat(JDBC_PROBE.closedResults).hasValue(4);
        assertThat(observed.closedDuringTransaction).hasValue(4);
        assertThat(jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id")).isEqualTo(withdrawals);
    }

    @Test
    void closesTheIdCursorAndRollsBackEarlierSavesWhenLoadingAggregatesFails() {
        insertBasic(205);
        insertWithdrawals();
        var withdrawals = jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id");
        observed.failOnLoad = true;

        assertThatThrownBy(useCase::updateBalances)
                .isInstanceOf(IllegalStateException.class).hasMessage("load failure");

        assertUnchanged();
        assertThat(observed.savedGroupSizes).containsExactly(100);
        assertThat(observed.loadedGroupSizes).containsExactly(100, 100);
        assertThat(JDBC_PROBE.closedResults).hasValue(1);
        assertThat(observed.closedDuringTransaction).hasValue(1);
        assertThat(jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id")).isEqualTo(withdrawals);
        assertReleased();
    }

    @Test
    void optimisticConflictRollsBackOtherAggregatesAndWithdrawalsInTheSameSaveAll() {
        insertBasic(2);
        insertWithdrawals();
        var withdrawals = jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id");
        var transaction = new TransactionTemplate(transactions);
        var snapshots = loadPendingSnapshots();
        snapshots.forEach(deposit -> {
            deposit.setBalance(9999.0);
            deposit.markCalculationProcessed();
        });
        jdbc.execute("UPDATE \"timeDeposits\" SET version = version + 1 WHERE id = 2");

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> observed.saveAll(snapshots)))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(balances()).containsExactly(1000.0, 1000.0);
        assertThat(jdbc.queryForList("SELECT version FROM \"timeDeposits\" ORDER BY id", Long.class)).containsExactly(0L, 1L);
        assertThat(jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id")).isEqualTo(withdrawals);
        assertReleased();
    }

    @Test
    void deletedAggregateFailsSaveAllAndRollsBackEarlierUpdates() {
        insertBasic(2);
        var transaction = new TransactionTemplate(transactions);
        var snapshots = loadPendingSnapshots();
        snapshots.forEach(deposit -> {
            deposit.setBalance(9999.0);
            deposit.markCalculationProcessed();
        });
        jdbc.execute("DELETE FROM \"timeDeposits\" WHERE id = 2");

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> observed.saveAll(snapshots)))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(balances()).containsExactly(1000.0);
        assertUnchanged();
        assertReleased();
    }

    @Test
    void savesCompleteAggregateWithoutRoundingBalancesOrLosingMetadataAndWithdrawals() {
        jdbc.execute("""
                INSERT INTO "timeDeposits" (id, "planType", balance, days, last_processed_days, version)
                VALUES (1, 'basic', 12.345, 31, 10, 7)
                """);
        jdbc.execute("""
                INSERT INTO withdrawals (id, "timeDepositId", amount, date)
                VALUES (11, 1, 2.50, DATE '2026-01-02'), (12, 1, 12.3456, DATE '2026-01-03')
                """);
        var withdrawals = jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id");
        double balance = 12.345 + 0.01;

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var snapshot = loadPendingSnapshots().getFirst();
            snapshot.setBalance(balance);
            snapshot.markCalculationProcessed();
            observed.saveAll(List.of(snapshot));
        });

        var actual = aggregates.findById(1).orElseThrow();
        assertThat(actual.planType()).isEqualTo("basic");
        assertThat(actual.days()).isEqualTo(31);
        assertThat(actual.lastProcessedDays()).isEqualTo(31);
        assertThat(actual.version()).isEqualTo(8L);
        assertThat(actual.withdrawals()).hasSize(2).filteredOn(withdrawal -> withdrawal.id() == 11)
                .singleElement().satisfies(withdrawal -> {
                    assertThat(withdrawal.id()).isEqualTo(11);
                    assertThat(withdrawal.amount()).isEqualByComparingTo("2.50");
                    assertThat(withdrawal.date()).isEqualTo(LocalDate.of(2026, 1, 2));
                });
        assertThat(jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id")).isEqualTo(withdrawals);
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
        insertWithdrawals();
        var withdrawals = jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id");
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
        assertThat(observed.closedDuringTransaction).hasValue(2);
        assertThat(jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id")).isEqualTo(withdrawals);
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

    private List<TimeDeposit> loadPendingSnapshots() {
        return new TransactionTemplate(transactions).execute(status -> {
            try (var ids = observed.streamPending()) {
                return observed.findAllById(ids.toList());
            }
        });
    }

    private void insertWithdrawals() {
        jdbc.execute("""
                INSERT INTO withdrawals ("timeDepositId", amount, date)
                SELECT id, 1.00, DATE '2026-01-02' FROM "timeDeposits"
                """);
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
        final List<Integer> savedGroupSizes = new CopyOnWriteArrayList<>();
        final List<Integer> loadedGroupSizes = new CopyOnWriteArrayList<>();
        final AtomicInteger closedDuringTransaction = new AtomicInteger();
        volatile boolean failOnWrite;
        volatile boolean failOnRead;
        volatile boolean failOnLoad;
        volatile Runnable onFirstLoad;
        volatile CyclicBarrier beforeFirstSave;

        ObservedRepository(TimeDepositRepositoryPort delegate) { this.delegate = delegate; }

        void reset() {
            savedGroupSizes.clear();
            loadedGroupSizes.clear();
            closedDuringTransaction.set(0);
            failOnWrite = false;
            failOnRead = false;
            failOnLoad = false;
            onFirstLoad = null;
            beforeFirstSave = null;
        }

        @Override
        public Stream<Integer> streamPending() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return delegate.streamPending().peek(id -> {
                if (failOnRead && id == 101) {
                    throw new IllegalStateException("read failure");
                }
            }).onClose(() -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                closedDuringTransaction.incrementAndGet();
            });
        }

        @Override
        public List<TimeDeposit> findAllById(List<Integer> ids) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(ids).hasSizeLessThanOrEqualTo(100);
            var deposits = delegate.findAllById(ids);
            loadedGroupSizes.add(ids.size());
            var hook = onFirstLoad;
            if (hook != null && ids.getFirst() == 1) {
                hook.run();
            }
            if (failOnLoad && ids.getFirst() > 100) {
                throw new IllegalStateException("load failure");
            }
            return deposits;
        }

        @Override
        public void saveAll(List<TimeDeposit> deposits) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            var barrier = beforeFirstSave;
            if (barrier != null && deposits.getFirst().getId() == 1) {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException("Concurrent requests did not reach the write barrier", exception);
                }
            }
            delegate.saveAll(deposits);
            savedGroupSizes.add(deposits.size());
            if (failOnWrite && deposits.getFirst().getId() > 100) {
                throw new IllegalStateException("write failure");
            }
        }
    }
}
