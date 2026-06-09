package com.hasan.apiwatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.enums.RequestAuthType;
import com.hasan.apiwatch.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceCredentialServiceTest {

    private static final String KEY =
            "YXBpd2F0Y2gtZGV2LWVuY3J5cHRpb24ta2V5LTMyYiE=";

    private ServiceCredentialService credentialService;
    private MonitoredService service;

    @BeforeEach
    void setUp() {
        credentialService = new ServiceCredentialService(
                new SecretEncryptionService(KEY),
                new ObjectMapper()
        );
        service = new MonitoredService();
    }

    @Test
    void encryptsHeadersAndBearerTokenBeforeApplyingThem() {
        credentialService.applyCreate(
                service,
                Map.of("X-Tenant", "customer-7"),
                RequestAuthType.BEARER,
                null,
                "secret-token"
        );

        assertThat(service.getCustomHeadersEncrypted()).doesNotContain("customer-7");
        assertThat(service.getAuthValueEncrypted()).doesNotContain("secret-token");
        assertThat(credentialService.customHeaderNames(service)).containsExactly("X-Tenant");
        assertThat(credentialService.hasAuthSecret(service)).isTrue();

        HttpHeaders headers = new HttpHeaders();
        credentialService.applyTo(service, headers);

        assertThat(headers.getFirst("X-Tenant")).isEqualTo("customer-7");
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer secret-token");
    }

    @Test
    void rejectsRestrictedAndMultilineHeaders() {
        assertThatThrownBy(() -> credentialService.applyCreate(
                service,
                Map.of("Host", "example.com"),
                RequestAuthType.NONE,
                null,
                null
        )).isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> credentialService.applyCreate(
                service,
                Map.of("X-Test", "one\r\ntwo"),
                RequestAuthType.NONE,
                null,
                null
        )).isInstanceOf(BadRequestException.class);
    }
}
