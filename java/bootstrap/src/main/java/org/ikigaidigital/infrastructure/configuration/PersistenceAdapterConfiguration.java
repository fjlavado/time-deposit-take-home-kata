package org.ikigaidigital.infrastructure.configuration;

import org.ikigaidigital.domain.port.out.TimeDepositRepository;
import org.ikigaidigital.infrastructure.persistence.JdbcTimeDepositRepository;
import org.ikigaidigital.infrastructure.persistence.mapper.TimeDepositPersistenceMapper;
import org.ikigaidigital.infrastructure.persistence.repository.TimeDepositJdbcRepository;
import org.ikigaidigital.infrastructure.persistence.repository.TimeDepositPageJdbcRepository;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;

@Configuration(proxyBeanMethods = false)
@EnableJdbcRepositories(basePackageClasses = TimeDepositJdbcRepository.class)
public class PersistenceAdapterConfiguration {
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
