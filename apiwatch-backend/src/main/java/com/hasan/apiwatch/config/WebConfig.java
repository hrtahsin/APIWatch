package com.hasan.apiwatch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String frontendOrigin;
    private final boolean allowLocalhostCors;

    public WebConfig(
            @Value("${apiwatch.frontend-origin}") String frontendOrigin,
            @Value("${apiwatch.security.allow-localhost-cors:false}") boolean allowLocalhostCors
    ) {
        this.frontendOrigin = frontendOrigin;
        this.allowLocalhostCors = allowLocalhostCors;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> allowedOrigins = new ArrayList<>();
        allowedOrigins.add(frontendOrigin);
        if (allowLocalhostCors) {
            allowedOrigins.add("http://localhost:*");
            allowedOrigins.add("http://127.0.0.1:*");
        }

        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Retry-After");
    }
}
