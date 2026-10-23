package org.ikigaidigital.infrastructure.configuration;

import org.ikigaidigital.infrastructure.web.TimeDepositController;
import org.ikigaidigital.infrastructure.web.mapper.TimeDepositApiMapper;
import org.ikigaidigital.infrastructure.web.configuration.SwaggerCorsConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({TimeDepositController.class, SwaggerCorsConfiguration.class})
public class WebAdapterConfiguration {
    @Bean
    TimeDepositApiMapper timeDepositApiMapper() {
        return new TimeDepositApiMapper();
    }
}
