package com.hasan.apiwatch;

import com.hasan.apiwatch.entity.HealthCheck;
import com.hasan.apiwatch.entity.Incident;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.entity.NotificationDelivery;
import com.hasan.apiwatch.enums.HealthStatus;
import com.hasan.apiwatch.enums.HttpMethodType;
import com.hasan.apiwatch.enums.IncidentStatus;
import com.hasan.apiwatch.enums.NotificationDeliveryStatus;
import com.hasan.apiwatch.enums.NotificationEventType;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import com.hasan.apiwatch.repository.IncidentRepository;
import com.hasan.apiwatch.repository.MonitoredServiceRepository;
import com.hasan.apiwatch.repository.NotificationDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "apiwatch.scheduler.enabled=false",
                "apiwatch.retention.enabled=false",
                "apiwatch.demo-data.enabled=false",
                "apiwatch.notifications.initial-delay-ms=3600000",
                "apiwatch.notifications.delivery-interval-ms=3600000",
                "apiwatch.retention.health-check-days=30",
                "apiwatch.retention.incident-days=30",
                "apiwatch.retention.notification-days=30",
                "apiwatch.auth.admin.username=",
                "apiwatch.auth.admin.password=",
                "apiwatch.auth.viewer.username=",
                "apiwatch.auth.viewer.password=",
                "apiwatch.scheduler.instance-id=postgres-it-scheduler",
                "apiwatch.notifications.instance-id=postgres-it-notifications"
        }
)
abstract class PostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected MonitoredServiceRepository serviceRepository;

    @Autowired
    protected HealthCheckRepository healthCheckRepository;

    @Autowired
    protected IncidentRepository incidentRepository;

    @Autowired
    protected NotificationDeliveryRepository deliveryRepository;

    @BeforeEach
    void resetApplicationTables() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    notification_deliveries,
                    monitoring_leases,
                    notification_settings,
                    audit_logs,
                    app_users,
                    incidents,
                    health_checks,
                    services
                RESTART IDENTITY CASCADE
                """);
    }

    protected MonitoredService saveService(String name) {
        MonitoredService service = new MonitoredService();
        service.setName(name);
        service.setUrl("https://example.com/" + name.toLowerCase().replace(' ', '-'));
        service.setMethod(HttpMethodType.GET);
        service.setExpectedStatusCode(200);
        service.setExpectedStatusMin(200);
        service.setExpectedStatusMax(299);
        service.setTimeoutMs(2000);
        service.setSlowThresholdMs(1000);
        service.setFailureThreshold(1);
        service.setActive(true);
        return serviceRepository.saveAndFlush(service);
    }

    protected HealthCheck saveCheck(
            MonitoredService service,
            HealthStatus status,
            Instant checkedAt
    ) {
        HealthCheck check = new HealthCheck();
        check.setMonitoredService(service);
        check.setStatus(status);
        check.setHttpStatusCode(status == HealthStatus.UP ? 200 : 503);
        check.setResponseTimeMs(25L);
        check.setCheckedAt(checkedAt);
        return healthCheckRepository.saveAndFlush(check);
    }

    protected Incident saveIncident(
            MonitoredService service,
            IncidentStatus status,
            Instant startedAt,
            Instant resolvedAt
    ) {
        Incident incident = new Incident();
        incident.setMonitoredService(service);
        incident.setStatus(status);
        incident.setReason("PostgreSQL integration test");
        incident.setStartedAt(startedAt);
        incident.setResolvedAt(resolvedAt);
        if (resolvedAt != null) {
            incident.setDurationSeconds(resolvedAt.getEpochSecond() - startedAt.getEpochSecond());
        }
        return incidentRepository.saveAndFlush(incident);
    }

    protected NotificationDelivery saveDelivery(
            Incident incident,
            NotificationDeliveryStatus status,
            String eventKey
    ) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setIncidentId(incident.getId());
        delivery.setServiceId(incident.getMonitoredService().getId());
        delivery.setEventType(NotificationEventType.INCIDENT_OPENED);
        delivery.setStatus(status);
        delivery.setEventKey(eventKey);
        return deliveryRepository.saveAndFlush(delivery);
    }
}
