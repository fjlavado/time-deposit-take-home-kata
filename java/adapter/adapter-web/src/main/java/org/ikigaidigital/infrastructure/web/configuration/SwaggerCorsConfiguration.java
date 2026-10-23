package org.ikigaidigital.infrastructure.web.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class SwaggerCorsConfiguration implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // The documented external Swagger UI runs on localhost:8081, outside this application's routes.
        registry.addMapping("/time-deposits").allowedOrigins("http://localhost:8081").allowedMethods("GET");
        registry.addMapping("/time-deposits/balance-updates")
                .allowedOrigins("http://localhost:8081").allowedMethods("POST");
    }
}
