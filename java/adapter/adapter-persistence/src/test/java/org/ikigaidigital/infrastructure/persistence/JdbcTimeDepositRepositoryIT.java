package org.ikigaidigital.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.ikigaidigital.domain.model.PlanType;
import org.ikigaidigital.domain.model.TimeDeposit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class JdbcTimeDepositRepositoryIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    private static Flyway flyway;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transaction;
    private JdbcTimeDepositRepository repository;

    @BeforeAll
    static void migrateFreshDatabase() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        flyway = Flyway.configure().dataSource(dataSource).load();
        flyway.migrate();
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new JdbcTransactionManager(dataSource));
    }

    @BeforeEach
    void resetData() {
        jdbc.execute("TRUNCATE withdrawals, \"timeDeposits\" RESTART IDENTITY");
        repository = new JdbcTimeDepositRepository(jdbc);
    }

    @Test
    void validatesVersionedSchemaAndDoesNotReapplyIt() {
        flyway.validate();

        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("1");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void retrievesEmptyDatabase() {
        assertThat(repository.findAll()).isEmpty();
        transaction.executeWithoutResult(status -> {
            assertThat(repository.findAllForBalanceUpdate()).isEmpty();
            repository.saveBalances(List.of());
        });
    }

    @Test
    void mapsDepositsWithMultipleOrNoWithdrawalsAndPreservesDatesAndDecimals() {
        jdbc.execute("""
                INSERT INTO "timeDeposits" (id, "planType", balance, days)
                VALUES (1, 'basic', 123.456, 31), (2, 'FuturePlan', 2000.10, 50)
                """);
        jdbc.execute("""
                INSERT INTO withdrawals (id, "timeDepositId", amount, date)
                VALUES (11, 1, 12.3456, DATE '2026-01-02'), (12, 1, 0.10, DATE '2026-01-03')
                """);

        var deposits = repository.findAll();

        assertThat(deposits).hasSize(2);
        assertThat(deposits.getFirst().balance()).isEqualTo(123.456);
        assertThat(deposits.getFirst().withdrawals()).hasSize(2);
        assertThat(deposits.getFirst().withdrawals().getFirst().amount()).isEqualByComparingTo("12.3456");
        assertThat(deposits.getFirst().withdrawals().getFirst().date()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(deposits.get(1).planType().value()).isEqualTo("FuturePlan");
        assertThat(deposits.get(1).withdrawals()).isEmpty();
    }

    @Test
    void savesOnlyBalancesWithoutRoundingTheResultOrOverwritingOtherFields() {
        jdbc.execute("INSERT INTO \"timeDeposits\" (id, \"planType\", balance, days) VALUES (1, 'basic', 12.345, 31)");
        jdbc.execute("INSERT INTO withdrawals (\"timeDepositId\", amount, date) VALUES (1, 2.50, DATE '2026-01-02')");
        double balance = 12.345 + 0.01;

        transaction.executeWithoutResult(status -> {
            repository.findAllForBalanceUpdate();
            // Deliberately different metadata verifies that persistence changes balances only.
            repository.saveBalances(List.of(new TimeDeposit(1, PlanType.PREMIUM, balance, 99, List.of())));
        });

        var actual = repository.findAll().getFirst();
        assertThat(actual.balance()).isEqualTo(balance);
        assertThat(actual.planType()).isEqualTo(PlanType.BASIC);
        assertThat(actual.days()).isEqualTo(31);
        assertThat(actual.withdrawals()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT balance FROM \"timeDeposits\" WHERE id = 1", BigDecimal.class))
                .isEqualByComparingTo(BigDecimal.valueOf(balance));
    }
}
