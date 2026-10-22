package org.ikigaidigital.infrastructure.configuration;

import org.ikigaidigital.infrastructure.web.TimeDepositController;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@Import(TimeDepositController.class)
public class WebAdapterConfiguration implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // The documented external Swagger UI runs on localhost:8081, outside this application's routes.
        registry.addMapping("/time-deposits").allowedOrigins("http://localhost:8081").allowedMethods("GET");
        registry.addMapping("/time-deposits/balance-updates")
                .allowedOrigins("http://localhost:8081").allowedMethods("POST");
    }
}
