package com.hasan.apiwatch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:apiwatch-openapi;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "apiwatch.scheduler.enabled=false",
        "apiwatch.api-version=0.1.0",
        "apiwatch.auth.admin.username=test-admin",
        "apiwatch.auth.admin.password=admin-password",
        "apiwatch.auth.viewer.username=test-viewer",
        "apiwatch.auth.viewer.password=viewer-password"
})
@AutoConfigureMockMvc
class OpenApiContractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectsTheApiContractWithAdministratorAccess() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v3/api-docs")
                        .with(httpBasic("test-viewer", "viewer-password")))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishesTheVersionedCoreApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .with(httpBasic("test-admin", "admin-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(
                        org.hamcrest.Matchers.startsWith("3.")
                ))
                .andExpect(jsonPath("$.info.title").value("APIWatch API"))
                .andExpect(jsonPath("$.info.version").value("0.1.0"))
                .andExpect(jsonPath("$.security[0].basicAuth").isArray())
                .andExpect(jsonPath(
                        "$.components.securitySchemes.basicAuth.type"
                ).value("http"))
                .andExpect(jsonPath(
                        "$.components.securitySchemes.basicAuth.scheme"
                ).value("basic"))
                .andExpect(jsonPath("$.paths['/api/auth/me']").exists())
                .andExpect(jsonPath("$.paths['/api/services']").exists())
                .andExpect(jsonPath("$.paths['/api/services/{id}']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/services/{serviceId}/check']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/services/{serviceId}/health-checks']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/services/{serviceId}/metrics']"
                ).exists())
                .andExpect(jsonPath("$.paths['/api/incidents']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/dashboard/summary']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/notification-settings']"
                ).exists())
                .andExpect(jsonPath("$.paths['/api/audit-logs']").exists());
    }

    @Test
    void protectsTheInteractiveDocumentationWithAdministratorAccess() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/swagger-ui.html")
                        .with(httpBasic("test-viewer", "viewer-password")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/swagger-ui.html")
                        .with(httpBasic("test-admin", "admin-password")))
                .andExpect(status().is3xxRedirection());
    }
}
