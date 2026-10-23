package org.ikigaidigital.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.aopalliance.intercept.MethodInterceptor;
import org.ikigaidigital.domain.interest.TimeDeposit;
import org.ikigaidigital.domain.interest.vo.PlanType;
import org.ikigaidigital.domain.model.PageRequestVO;
import org.ikigaidigital.infrastructure.persistence.mapper.TimeDepositPersistenceMapper;
import org.ikigaidigital.infrastructure.persistence.repository.TimeDepositJdbcRepository;
import org.ikigaidigital.infrastructure.persistence.repository.TimeDepositPageJdbcRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.relational.RelationalManagedTypes;
import org.springframework.data.relational.core.mapping.NamingStrategy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class JdbcTimeDepositRepositoryIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    private static Flyway flyway;
    private static JdbcTemplate jdbc;
    private static AnnotationConfigApplicationContext context;
    private static final AtomicInteger READ_QUERIES = new AtomicInteger();
    private JdbcTimeDepositRepository repository;

    @BeforeAll
    static void migrateFreshDatabase() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()) {
            @Override
            public Connection getConnection() throws SQLException {
                return countingReads(super.getConnection());
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return countingReads(super.getConnection(username, password));
            }
        };
        flyway = Flyway.configure().dataSource(dataSource).load();
        flyway.migrate();
        jdbc = new JdbcTemplate(dataSource);
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> dataSource);
        context.register(JdbcConfiguration.class);
        context.refresh();
    }

    @AfterAll
    static void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    private static Connection countingReads(Connection connection) {
        var proxy = new ProxyFactory();
        proxy.setInterfaces(Connection.class);
        proxy.setTarget(connection);
        proxy.addAdvice((MethodInterceptor) invocation -> {
            if (invocation.getMethod().getName().equals("prepareStatement")) {
                String sql = ((String) invocation.getArguments()[0]).stripLeading().toUpperCase(Locale.ROOT);
                if (sql.startsWith("SELECT") || sql.startsWith("WITH")) {
                    READ_QUERIES.incrementAndGet();
                }
            }
            var result = invocation.proceed();
            if (invocation.getMethod().getName().equals("createStatement")) {
                var statementProxy = new ProxyFactory();
                statementProxy.setInterfaces(Statement.class);
                statementProxy.setTarget(result);
                statementProxy.addAdvice((MethodInterceptor) statementCall -> {
                    if (statementCall.getMethod().getName().equals("executeQuery")) {
                        READ_QUERIES.incrementAndGet();
                    }
                    return statementCall.proceed();
                });
                return statementProxy.getProxy();
            }
            return result;
        });
        return (Connection) proxy.getProxy();
    }

    @BeforeEach
    void resetData() {
        jdbc.execute("TRUNCATE withdrawals, \"timeDeposits\" RESTART IDENTITY");
        repository = new JdbcTimeDepositRepository(context.getBean(TimeDepositJdbcRepository.class),
                context.getBean(TimeDepositPageJdbcRepository.class),
                new TimeDepositPersistenceMapper());
    }

    @Test
    void validatesVersionedSchemaAndDoesNotReapplyIt() {
        flyway.validate();

        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("3");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void retrievesEmptyDatabase() {
        READ_QUERIES.set(0);
        var page = repository.search(new PageRequestVO(0, 20));
        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(READ_QUERIES.get()).isEqualTo(1);
    }

    @Test
    void loadsMappedWithdrawalsWithoutPerDepositQueriesWithOrderingDatesAndDecimals() {
        jdbc.execute("""
                INSERT INTO "timeDeposits" (id, "planType", balance, days)
                VALUES (2, 'FuturePlan', 2000.10, 50), (1, 'basic', 123.456, 31)
                """);
        jdbc.execute("""
                INSERT INTO withdrawals (id, "timeDepositId", amount, date)
                VALUES (12, 1, 0.10, DATE '2026-01-03'), (11, 1, 12.3456, DATE '2026-01-02'),
                       (13, 2, 7.89, DATE '2026-01-04')
                """);
        jdbc.execute("INSERT INTO \"timeDeposits\" (id, \"planType\", balance, days) VALUES (3, 'premium', 3000, 46)");

        READ_QUERIES.set(0);
        var page = repository.search(new PageRequestVO(0, 20));
        var deposits = page.content();

        assertThat(READ_QUERIES.get()).as("one ID page plus one aggregate query, including all withdrawals")
                .isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(deposits).extracting(TimeDeposit::getId).containsExactly(1, 2, 3);
        assertThat(deposits.getFirst().getPlanType()).isEqualTo(PlanType.BASIC);
        assertThat(deposits.getFirst().getDays()).isEqualTo(31);
        assertThat(deposits.getFirst().getBalance()).isEqualTo(123.456);
        assertThat(deposits.getFirst().getWithdrawals()).extracting(withdrawal -> withdrawal.id())
                .containsExactly(11, 12);
        assertThat(deposits.getFirst().getWithdrawals().getFirst().amount()).isEqualByComparingTo("12.3456");
        assertThat(deposits.getFirst().getWithdrawals().getFirst().date()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(deposits.get(1).getPlanType()).isEqualTo(new PlanType("FuturePlan"));
        assertThat(deposits.get(1).getWithdrawals()).singleElement().satisfies(withdrawal -> {
            assertThat(withdrawal.id()).isEqualTo(13);
            assertThat(withdrawal.amount()).isEqualByComparingTo("7.89");
            assertThat(withdrawal.date()).isEqualTo(LocalDate.of(2026, 1, 4));
        });
        assertThat(deposits.get(2).getWithdrawals()).isEmpty();
    }

    @Test
    void usesInheritedPagingWithDatabaseOrderingAndNoPerDepositQueries() {
        jdbc.execute("""
                INSERT INTO "timeDeposits" (id, "planType", balance, days)
                VALUES (3, 'premium', 3000, 46), (1, 'basic', 1000, 31), (2, 'student', 2000, 365)
                """);
        jdbc.execute("""
                INSERT INTO withdrawals (id, "timeDepositId", amount, date)
                VALUES (12, 1, 2.50, DATE '2026-01-02'), (11, 1, 1.25, DATE '2026-01-01'),
                       (13, 2, 3.75, DATE '2026-01-03')
                """);

        READ_QUERIES.set(0);
        var first = repository.search(new PageRequestVO(0, 2));

        assertThat(READ_QUERIES.get()).as("one ID page, one count, one aggregate read; no child queries")
                .isEqualTo(3);
        assertThat(first.content()).extracting(TimeDeposit::getId).containsExactly(1, 2);
        assertThat(first.content().getFirst().getWithdrawals()).extracting(withdrawal -> withdrawal.id())
                .containsExactly(11, 12);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);

        var last = repository.search(new PageRequestVO(1, 2));
        assertThat(last.content()).extracting(TimeDeposit::getId).containsExactly(3);
        assertThat(last.page()).isEqualTo(1);
        assertThat(last.size()).isEqualTo(2);
        assertThat(last.totalElements()).isEqualTo(3);
        assertThat(last.totalPages()).isEqualTo(2);

        var beyond = repository.search(new PageRequestVO(2, 2));
        assertThat(beyond.content()).isEmpty();
        assertThat(beyond.totalElements()).isEqualTo(3);
        assertThat(beyond.totalPages()).isEqualTo(2);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableJdbcRepositories(basePackageClasses = TimeDepositJdbcRepository.class)
    static class JdbcConfiguration extends AbstractJdbcConfiguration {
        @Override
        @Bean
        public JdbcMappingContext jdbcMappingContext(Optional<NamingStrategy> namingStrategy,
                                                     JdbcCustomConversions conversions,
                                                     RelationalManagedTypes managedTypes) {
            var context = super.jdbcMappingContext(namingStrategy, conversions, managedTypes);
            context.setSingleQueryLoadingEnabled(true);
            return context;
        }

        @Bean
        NamedParameterJdbcOperations namedParameterJdbcOperations(DataSource dataSource) {
            return new NamedParameterJdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new JdbcTransactionManager(dataSource);
        }
    }
}
