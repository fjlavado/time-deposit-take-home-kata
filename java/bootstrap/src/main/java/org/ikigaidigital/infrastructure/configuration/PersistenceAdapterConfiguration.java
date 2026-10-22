package org.ikigaidigital.infrastructure.configuration;

import org.ikigaidigital.domain.port.out.TimeDepositRepository;
import org.ikigaidigital.infrastructure.persistence.JdbcTimeDepositRepository;
import org.ikigaidigital.infrastructure.persistence.mapper.TimeDepositResultSetMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class PersistenceAdapterConfiguration {
    @Bean
    TimeDepositResultSetMapper timeDepositResultSetMapper() {
        return new TimeDepositResultSetMapper();
    }

    @Bean
    TimeDepositRepository timeDepositRepository(JdbcTemplate jdbc, TimeDepositResultSetMapper mapper) {
        return new JdbcTimeDepositRepository(jdbc, mapper);
    }
}
