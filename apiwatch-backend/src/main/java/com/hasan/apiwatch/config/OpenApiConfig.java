package com.hasan.apiwatch.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static final String BASIC_AUTH_SCHEME = "basicAuth";

    @Bean
    OpenAPI apiWatchOpenApi(
            @Value("${apiwatch.api-version}") String apiVersion
    ) {
        return new OpenAPI()
                .info(new Info()
                        .title("APIWatch API")
                        .version(apiVersion)
                        .description("API monitoring, incident management, and operational telemetry"))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH_SCHEME))
                .schemaRequirement(BASIC_AUTH_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic")
                        .description("APIWatch administrator or viewer credentials"));
    }
}
