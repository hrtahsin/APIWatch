package com.hasan.apiwatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.hasan.apiwatch.entity.NotificationDelivery;
import com.hasan.apiwatch.enums.NotificationDeliveryStatus;
import com.hasan.apiwatch.enums.NotificationEventType;
import com.hasan.apiwatch.repository.NotificationDeliveryRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import com.hasan.apiwatch.repository.IncidentRepository;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:apiwatch-workflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "apiwatch.scheduler.enabled=false",
        "apiwatch.retention.enabled=false",
        "apiwatch.auth.admin.username=test-admin",
        "apiwatch.auth.admin.password=admin-password",
        "apiwatch.auth.viewer.username=test-viewer",
        "apiwatch.auth.viewer.password=viewer-password",
        "apiwatch.security.private-target-allowlist=localhost"
})
@AutoConfigureMockMvc
class MonitoringWorkflowIntegrationTest {

    private static final AtomicBoolean FLAKY_HEALTHY = new AtomicBoolean(false);
    private static HttpServer server;
    private static String baseUrl;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HealthCheckRepository healthCheckRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/rate-limit", exchange -> {
            exchange.getResponseHeaders().add("Retry-After", "60");
            respond(exchange, 429, "rate limited");
        });
        server.createContext("/flaky", exchange ->
                respond(exchange, FLAKY_HEALTHY.get() ? 200 : 503, "status")
        );
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void rateLimitResponsePausesRepeatedChecks() throws Exception {
        long serviceId = createService("Rate Limited API", baseUrl + "/rate-limit", 1);

        mockMvc.perform(post("/api/services/{id}/check", serviceId)
                        .with(httpBasic("test-admin", "admin-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(60));

        mockMvc.perform(post("/api/services/{id}/check", serviceId)
                        .with(httpBasic("test-admin", "admin-password")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("rate limited")
                ));
    }

    @Test
    void incidentOpensAfterThresholdAndResolvesOnRecovery() throws Exception {
        FLAKY_HEALTHY.set(false);
        long serviceId = createService("Flaky API", baseUrl + "/flaky", 2);

        runCheck(serviceId, "DOWN");
        runCheck(serviceId, "DOWN");

        mockMvc.perform(get("/api/incidents")
                        .param("serviceId", Long.toString(serviceId))
                        .with(httpBasic("test-viewer", "viewer-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));

        FLAKY_HEALTHY.set(true);
        runCheck(serviceId, "UP");

        mockMvc.perform(get("/api/incidents")
                        .param("serviceId", Long.toString(serviceId))
                        .with(httpBasic("test-viewer", "viewer-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$.content[0].resolvedAt").isNotEmpty());
    }

    @Test
    void deletingServiceRemovesChecksAndIncidents() throws Exception {
        FLAKY_HEALTHY.set(false);
        long serviceId = createService("Delete API", baseUrl + "/flaky", 2);
        runCheck(serviceId, "DOWN");
        runCheck(serviceId, "DOWN");
        var incident = incidentRepository
                .findByMonitoredServiceIdOrderByStartedAtDesc(serviceId)
                .getFirst();
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setIncidentId(incident.getId());
        delivery.setServiceId(serviceId);
        delivery.setEventType(NotificationEventType.INCIDENT_OPENED);
        delivery.setStatus(NotificationDeliveryStatus.SENT);
        notificationDeliveryRepository.save(delivery);

        mockMvc.perform(delete("/api/services/{id}", serviceId)
                        .with(httpBasic("test-admin", "admin-password")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/services/{id}", serviceId)
                        .with(httpBasic("test-viewer", "viewer-password")))
                .andExpect(status().isNotFound());
        assertThat(healthCheckRepository
                .findByMonitoredServiceIdOrderByCheckedAtDesc(
                        serviceId,
                        PageRequest.of(0, 10)
                )
                .getContent()).isEmpty();
        assertThat(incidentRepository.findByMonitoredServiceIdOrderByStartedAtDesc(serviceId))
                .isEmpty();
        assertThat(notificationDeliveryRepository.findAll()).isEmpty();
    }

    private long createService(String name, String url, int failureThreshold) throws Exception {
        String response = mockMvc.perform(post("/api/services")
                        .with(httpBasic("test-admin", "admin-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "url": "%s",
                                  "method": "GET",
                                  "expectedStatusMin": 200,
                                  "expectedStatusMax": 299,
                                  "timeoutMs": 2000,
                                  "checkIntervalSeconds": 60,
                                  "failureThreshold": %d,
                                  "active": true,
                                  "authType": "NONE"
                                }
                                """.formatted(name, url, failureThreshold)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("id").asLong();
    }

    private void runCheck(long serviceId, String expectedStatus) throws Exception {
        mockMvc.perform(post("/api/services/{id}/check", serviceId)
                        .with(httpBasic("test-admin", "admin-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }

    private static void respond(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
