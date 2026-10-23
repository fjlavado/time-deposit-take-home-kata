package org.ikigaidigital.infrastructure.configuration;

import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.ikigaidigital.application.port.out.TimeDepositRepositoryPort;
import org.ikigaidigital.application.port.out.BalanceUpdateExecutionRepository;
import org.ikigaidigital.infrastructure.persistence.JdbcBalanceUpdateExecutionRepository;
import org.ikigaidigital.infrastructure.persistence.JdbcTimeDepositRepository;
import org.ikigaidigital.infrastructure.persistence.TimeDepositRepositoryAdapter;
import org.ikigaidigital.infrastructure.persistence.mapper.TimeDepositPersistenceMapper;
import org.ikigaidigital.infrastructure.persistence.repository.TimeDepositJdbcRepository;
import org.ikigaidigital.infrastructure.persistence.repository.TimeDepositPageJdbcRepository;
import org.ikigaidigital.infrastructure.persistence.repository.TimeDepositBalanceJdbcRepository;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@EnableJdbcRepositories(basePackageClasses = TimeDepositJdbcRepository.class,
        jdbcOperationsRef = "depositJdbcOperations")
public class PersistenceAdapterConfiguration {
    @Bean
    BalanceUpdateExecutionRepository balanceUpdateExecutionRepository(JdbcTemplate jdbc) {
        return new JdbcBalanceUpdateExecutionRepository(jdbc);
    }

    @Bean
    NamedParameterJdbcTemplate depositJdbcOperations(DataSource dataSource) {
        var template = new JdbcTemplate(dataSource);
        // A positive fetch size and the outer transaction enable PostgreSQL server-side fetching.
        template.setFetchSize(100);
        return new NamedParameterJdbcTemplate(template);
    }

    @Bean
    TimeDepositRepositoryPort balanceUpdateRepository(TimeDepositBalanceJdbcRepository deposits,
                                                      TimeDepositPersistenceMapper mapper) {
        return new TimeDepositRepositoryAdapter(deposits, mapper);
    }

    @Bean
    static BeanPostProcessor singleQueryLoading() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof JdbcMappingContext context) {
                    // Mapped collections otherwise require additional child queries per aggregate.
                    context.setSingleQueryLoadingEnabled(true);
                }
                return bean;
            }
        };
    }

    @Bean
    TimeDepositPersistenceMapper timeDepositPersistenceMapper() {
        return new TimeDepositPersistenceMapper();
    }

    @Bean
    TimeDepositRepository timeDepositRepository(TimeDepositJdbcRepository deposits,
                                                TimeDepositPageJdbcRepository pages,
                                                TimeDepositPersistenceMapper mapper) {
        return new JdbcTimeDepositRepository(deposits, pages, mapper);
    }
}
