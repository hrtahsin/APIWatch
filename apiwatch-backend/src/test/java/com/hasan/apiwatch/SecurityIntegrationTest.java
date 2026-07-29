package com.hasan.apiwatch;

import com.hasan.apiwatch.enums.UserRole;
import com.hasan.apiwatch.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:apiwatch-security;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "apiwatch.scheduler.enabled=false",
        "apiwatch.auth.admin.username=test-admin",
        "apiwatch.auth.admin.password=admin-password",
        "apiwatch.auth.viewer.username=test-viewer",
        "apiwatch.auth.viewer.password=viewer-password",
        "apiwatch.frontend-origin=https://dashboard.example",
        "apiwatch.security.allow-localhost-cors=false"
})
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Test
    void requiresAuthenticationForApiReads() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(header().string(
                        "Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'"
                ))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string(
                        "Permissions-Policy",
                        "camera=(), microphone=(), geolocation=()"
                ));
    }

    @Test
    void exposesNonSensitiveHealthProbesWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void restrictsPrometheusMetricsToAdministrators() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus")
                        .with(httpBasic("test-viewer", "viewer-password")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/prometheus")
                        .with(httpBasic("test-admin", "admin-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "apiwatch_services_active"
                )));
    }

    @Test
    void echoesSafeRequestIdsAndReplacesUnsafeValues() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")
                        .header("X-Request-Id", "deployment-check-42"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "deployment-check-42"));

        String generated = mockMvc.perform(get("/actuator/health/liveness")
                        .header("X-Request-Id", "unsafe request id"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        assertThat(generated)
                .isNotEqualTo("unsafe request id")
                .matches("[0-9a-f-]{36}");
    }

    @Test
    void allowsOnlyTheConfiguredCorsOrigin() throws Exception {
        mockMvc.perform(options("/api/services")
                        .header("Origin", "https://dashboard.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "https://dashboard.example"
                ));

        mockMvc.perform(options("/api/services")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void demoEndpointsAreUnavailableByDefault() throws Exception {
        mockMvc.perform(get("/api/mock/healthy"))
                .andExpect(status().isNotFound());
    }

    @Test
    void viewerCanReadButCannotMutate() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .with(httpBasic("test-viewer", "viewer-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEWER"));

        mockMvc.perform(patch("/api/incidents/999/resolve")
                        .with(httpBasic("test-viewer", "viewer-password")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Administrator access is required"));
    }

    @Test
    void administratorCanReachMutationEndpoints() throws Exception {
        mockMvc.perform(patch("/api/incidents/999/resolve")
                        .with(httpBasic("test-admin", "admin-password")))
                .andExpect(status().isNotFound());
    }

    @Test
    void auditLogsAreAdminOnly() throws Exception {
        mockMvc.perform(get("/api/audit-logs")
                        .with(httpBasic("test-viewer", "viewer-password")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/audit-logs")
                        .with(httpBasic("test-admin", "admin-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void bootstrapUsersArePersistedWithEncodedPasswords() {
        var admin = userRepository.findByUsernameIgnoreCase("test-admin").orElseThrow();
        var viewer = userRepository.findByUsernameIgnoreCase("test-viewer").orElseThrow();

        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getPasswordHash()).startsWith("$2");
        assertThat(admin.getPasswordHash()).doesNotContain("admin-password");
        assertThat(viewer.getRole()).isEqualTo(UserRole.VIEWER);
        assertThat(viewer.getPasswordHash()).startsWith("$2");
        assertThat(viewer.getPasswordHash()).doesNotContain("viewer-password");
    }
}
