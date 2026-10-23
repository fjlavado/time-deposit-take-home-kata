package org.ikigaidigital.infrastructure.configuration;

import org.ikigaidigital.application.port.in.QueryBus;
import org.ikigaidigital.application.port.in.UpdateDepositBalancesUseCase;
import org.ikigaidigital.application.query.RetrieveTimeDepositsQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class DevProfileIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchema() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
    }

    @Test
    void defaultProfileMigratesWithoutLoadingSampleData() {
        try (var context = start("")) {
            assertThat(context.getEnvironment().getActiveProfiles()).isEmpty();
            assertCounts(0, 0);
        }
    }

    @Test
    void devProfileSeedsPaginatedAggregatesAndUpdatesEveryGroupWithTheExpectedRules() {
        try (var context = start("dev")) {
            assertCounts(120, 120);
            var queries = context.getBean(QueryBus.class);
            var first = queries.dispatch(new RetrieveTimeDepositsQuery(0, 20));
            assertThat(first.content()).hasSize(20);
            assertThat(first.totalElements()).isEqualTo(120);
            assertThat(queries.dispatch(new RetrieveTimeDepositsQuery(5, 20)).content()).hasSize(20);
            assertThat(first.content().getFirst().getWithdrawals()).isEmpty();
            var withdrawals = first.content().get(1).getWithdrawals();
            assertThat(withdrawals).hasSize(2);
            assertThat(withdrawals.getFirst().amount()).isEqualByComparingTo("12.34");
            assertThat(withdrawals.getFirst().date()).isEqualTo(LocalDate.of(2026, 1, 2));
            var beforeWithdrawals = jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id");

            context.getBean(UpdateDepositBalancesUseCase.class).updateBalances();

            assertThat(jdbc.queryForList("SELECT balance FROM \"timeDeposits\" WHERE id <= 12 ORDER BY id", Double.class))
                    .containsExactly(1000.0, 1000.83, 1000.0, 1002.5, 1002.5, 1000.0,
                            1000.0, 1004.17, 18.02, 6.02, 1000.0, 0.0);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM \"timeDeposits\" WHERE version = 1", Integer.class))
                    .isEqualTo(120);
            assertThat(jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id")).isEqualTo(beforeWithdrawals);
        }
    }

    @Test
    void restartingDevPreservesChangedDataAndSwitchingToDefaultValidatesTheSameSchema() {
        try (var ignored = start("dev")) {
            jdbc.update("UPDATE \"timeDeposits\" SET balance = 77.77, version = 3 WHERE id = 2");
            jdbc.update("UPDATE withdrawals SET amount = 0.55 WHERE id = 1");
        }
        var deposits = jdbc.queryForList("SELECT * FROM \"timeDeposits\" ORDER BY id");
        var withdrawals = jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id");
        for (var profile : new String[]{"dev", ""}) {
            try (var ignored = start(profile)) {
                assertCounts(120, 120);
                assertThat(jdbc.queryForList("SELECT * FROM \"timeDeposits\" ORDER BY id")).isEqualTo(deposits);
                assertThat(jdbc.queryForList("SELECT * FROM withdrawals ORDER BY id")).isEqualTo(withdrawals);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version IS NOT NULL",
                        Integer.class)).isEqualTo(4);
            }
        }
    }

    @Test
    void enablingDevOnAnExistingDatabaseDoesNotAddOrReplaceDeposits() {
        try (var ignored = start("")) {
            jdbc.execute("INSERT INTO \"timeDeposits\" (\"planType\", balance, days) VALUES ('custom', 123.45, 80)");
        }
        try (var ignored = start("dev")) {
            assertCounts(1, 0);
            assertThat(jdbc.queryForObject("SELECT balance FROM \"timeDeposits\"", BigDecimal.class))
                    .isEqualByComparingTo("123.45");
        }
    }

    private ConfigurableApplicationContext start(String profile) {
        return new SpringApplicationBuilder(TimeDepositApplication.class).web(WebApplicationType.NONE).run(
                "--spring.profiles.active=" + profile,
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword());
    }

    private void assertCounts(int deposits, int withdrawals) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"timeDeposits\"", Integer.class)).isEqualTo(deposits);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM withdrawals", Integer.class)).isEqualTo(withdrawals);
    }
}
