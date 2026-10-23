package org.ikigaidigital.infrastructure.configuration;

import org.ikigaidigital.application.command.StartBalanceUpdateJobCommand;
import org.ikigaidigital.application.command.handler.StartBalanceUpdateJobCommandHandler;
import org.ikigaidigital.application.job.BalanceUpdateJobResult;
import org.ikigaidigital.application.job.BalanceUpdateExecutionStatus;
import org.ikigaidigital.application.job.BackgroundBalanceUpdateJob;
import org.ikigaidigital.infrastructure.execution.ExecutorBackgroundTaskExecutor;
import org.ikigaidigital.application.port.in.UpdateDepositBalancesUseCase;
import org.ikigaidigital.application.service.UpdateDepositBalancesService;
import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.application.port.out.BalanceUpdateExecutionRepository;
import org.ikigaidigital.application.port.out.TimeDepositRepositoryPort;
import org.ikigaidigital.services.TimeDepositCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class BackgroundBalanceUpdateJobIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired BalanceUpdateExecutionRepository executions;
    @Autowired TimeDepositRepositoryPort deposits;
    @Autowired UpdateDepositBalancesUseCase useCase;
    private final TimeDepositCalculator calculator = new TimeDepositCalculator();
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE balance_update_executions, withdrawals, \"timeDeposits\" RESTART IDENTITY");
        jdbc.execute("INSERT INTO \"timeDeposits\" (id, \"planType\", balance, days) VALUES (1, 'basic', 1000, 31)");
    }

    @Test
    void concurrentHandlersOnIndependentRunnersReserveOnlyOneUuidAndExecuteOnce() throws Exception {
        var queued = new ConcurrentLinkedQueue<Runnable>();
        // Independent runner/handler instances deliberately share no JVM admission lock.
        var first = new StartBalanceUpdateJobCommandHandler(job(queued::add, useCase));
        var second = new StartBalanceUpdateJobCommandHandler(job(queued::add, useCase));
        var ready = new CountDownLatch(8);
        var go = new CountDownLatch(1);
        try (var callers = Executors.newFixedThreadPool(8); var worker = Executors.newSingleThreadExecutor()) {
            var futures = IntStream.range(0, 8).mapToObj(index -> callers.submit(() -> {
                ready.countDown();
                await(go);
                return (index % 2 == 0 ? first : second).handle(new StartBalanceUpdateJobCommand());
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            var results = new java.util.ArrayList<BalanceUpdateJobResult>();
            for (var future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            assertThat(results).filteredOn(result -> result.status() == BalanceUpdateJobResult.Status.STARTED).hasSize(1);
            assertThat(results).filteredOn(result -> result.status() == BalanceUpdateJobResult.Status.RUNNING).hasSize(7);
            var id = results.getFirst().jobId();
            assertThat(id.version()).isEqualTo(4);
            assertThat(results).extracting(BalanceUpdateJobResult::jobId).containsOnly(id);
            assertThat(queued).hasSize(1);
            assertThat(status(id)).isEqualTo("STARTED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM balance_update_executions", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT balance FROM \"timeDeposits\" WHERE id = 1", Double.class)).isEqualTo(1000);

            worker.submit(queued.remove()).get(10, TimeUnit.SECONDS);
            assertThat(status(id)).isEqualTo("COMPLETED");
            assertThat(jdbc.queryForObject("SELECT balance FROM \"timeDeposits\" WHERE id = 1", Double.class)).isEqualTo(1000.83);
            assertThat(jdbc.queryForObject("SELECT finished_at >= started_at FROM balance_update_executions WHERE id = ?",
                    Boolean.class, id)).isTrue();

            var next = second.handle(new StartBalanceUpdateJobCommand());
            assertThat(next.status()).isEqualTo(BalanceUpdateJobResult.Status.STARTED);
            assertThat(next.jobId()).isNotEqualTo(id);
            worker.submit(queued.remove()).get(10, TimeUnit.SECONDS);
            assertThat(status(next.jobId())).isEqualTo("COMPLETED");
            assertThat(jdbc.queryForObject("SELECT version FROM \"timeDeposits\" WHERE id = 1", Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM balance_update_executions", Integer.class)).isEqualTo(2);
        } finally {
            go.countDown();
        }
    }

    @Test
    void databaseConstraintRejectsASecondActiveExecutionEvenOutsideTheHandler() {
        var queued = new ConcurrentLinkedQueue<Runnable>();
        var result = job(queued::add, useCase).start();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO balance_update_executions (id, status, started_at) VALUES (?, 'RUNNING', CURRENT_TIMESTAMP)
                """, UUID.randomUUID())).isInstanceOf(DuplicateKeyException.class);
        assertThat(status(result.jobId())).isEqualTo("STARTED");
    }

    @Test
    void returnsRunningWhileWorkerIsBlockedAndRecordsCompletionAfterCommit() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var queued = new ConcurrentLinkedQueue<Runnable>();
        var runner = job(queued::add, () -> {
            entered.countDown();
            await(release);
            useCase.updateBalances();
        });
        var result = runner.start();
        try (var worker = Executors.newSingleThreadExecutor()) {
            var future = worker.submit(queued.remove());
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(status(result.jobId())).isEqualTo("RUNNING");
                var busy = job(queued::add, useCase).start();
                assertThat(busy).isEqualTo(new BalanceUpdateJobResult(result.jobId(), BalanceUpdateJobResult.Status.RUNNING));
                assertThat(queued).isEmpty();
            } finally {
                release.countDown();
            }
            future.get(10, TimeUnit.SECONDS);
            assertThat(status(result.jobId())).isEqualTo("COMPLETED");
        }
    }

    @Test
    void laterGroupFailureRollsBackBalancesAndWithdrawalsButKeepsFailedAuditAndAllowsRetry() {
        jdbc.execute("""
                INSERT INTO "timeDeposits" (id, "planType", balance, days)
                SELECT id, 'basic', 1000, 31 FROM generate_series(2, 205) AS id
                """);
        jdbc.execute("INSERT INTO withdrawals (\"timeDepositId\", amount, date) VALUES (1, 12.34, DATE '2026-01-02')");
        var withdrawals = jdbc.queryForList("SELECT * FROM withdrawals");
        var failing = new TimeDepositRepositoryPort() {
            public Stream<Integer> streamAllIds() { return deposits.streamAllIds(); }
            public List<TimeDeposit> findAllById(List<Integer> ids) { return deposits.findAllById(ids); }
            public void saveAll(List<TimeDeposit> group) {
                deposits.saveAll(group);
                if (group.getFirst().getId() > 100) {
                    throw new IllegalStateException("Simulated later-group failure");
                }
            }
        };
        var service = new UpdateDepositBalancesService(failing, calculator);
        UpdateDepositBalancesUseCase failingUseCase = () -> new TransactionTemplate(transactions)
                .executeWithoutResult(tx -> service.updateBalances());
        var queued = new ConcurrentLinkedQueue<Runnable>();
        var failed = job(queued::add, failingUseCase).start();
        queued.remove().run();

        assertThat(status(failed.jobId())).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"timeDeposits\" WHERE balance <> 1000 OR version <> 0", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM withdrawals")).isEqualTo(withdrawals);
        var retry = job(queued::add, useCase).start();
        assertThat(retry.status()).isEqualTo(BalanceUpdateJobResult.Status.STARTED);
        queued.remove().run();
        assertThat(status(retry.jobId())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"timeDeposits\" WHERE balance = 1000.83", Integer.class)).isEqualTo(205);
    }

    @Test
    void admissionAndAuditCommitIndependentlyOfARollingBackCallerTransaction() {
        var queued = new ConcurrentLinkedQueue<Runnable>();
        var id = new AtomicReference<UUID>();
        var caller = new TransactionTemplate(transactions);
        caller.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        assertThatThrownBy(() -> caller.executeWithoutResult(tx -> {
            var result = job(queued::add, useCase).start();
            id.set(result.jobId());
            assertThat(result.status()).isEqualTo(BalanceUpdateJobResult.Status.STARTED);
            executions.update(id.get(), BalanceUpdateExecutionStatus.FAILED, Instant.now());
            assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            jdbc.update("UPDATE \"timeDeposits\" SET balance = 999 WHERE id = 1");
            throw new IllegalStateException("Roll back the caller, not its committed audit");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(status(id.get())).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT balance FROM \"timeDeposits\" WHERE id = 1", Double.class))
                .isEqualTo(1000);
        assertThat(queued).hasSize(1);
    }

    @Test
    void executorRejectionRecordsFailureAndReleasesDatabaseReservation() {
        var rejected = job(task -> { throw new RejectedExecutionException("Stopped executor"); }, useCase);
        assertThatThrownBy(rejected::start).isInstanceOf(RejectedExecutionException.class);
        assertThat(jdbc.queryForList("SELECT status FROM balance_update_executions", String.class)).containsExactly("FAILED");
        var queued = new ConcurrentLinkedQueue<Runnable>();
        assertThat(job(queued::add, useCase).start().status()).isEqualTo(BalanceUpdateJobResult.Status.STARTED);
    }

    private BackgroundBalanceUpdateJob job(Executor executor, UpdateDepositBalancesUseCase balances) {
        return new BackgroundBalanceUpdateJob(new ExecutorBackgroundTaskExecutor(executor), balances, executions);
    }

    private String status(UUID id) {
        return jdbc.queryForObject("SELECT status FROM balance_update_executions WHERE id = ?", String.class, id);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
