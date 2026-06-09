package com.hasan.apiwatch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.enums.RequestAuthType;
import com.hasan.apiwatch.exception.BadRequestException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ServiceCredentialService {

    private static final Pattern HEADER_NAME =
            Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");
    private static final Set<String> RESTRICTED_HEADERS = Set.of(
            "host",
            "content-length",
            "connection",
            "transfer-encoding"
    );
    private static final int MAX_HEADERS = 20;
    private static final int MAX_VALUE_LENGTH = 4096;

    private final SecretEncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    public ServiceCredentialService(
            SecretEncryptionService encryptionService,
            ObjectMapper objectMapper
    ) {
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
    }

    public void applyCreate(
            MonitoredService service,
            Map<String, String> customHeaders,
            RequestAuthType authType,
            String authHeaderName,
            String authValue
    ) {
        service.setCustomHeadersEncrypted(encryptHeaders(customHeaders));
        applyAuth(service, authType, authHeaderName, authValue, false);
    }

    public void applyUpdate(
            MonitoredService service,
            Map<String, String> customHeaders,
            RequestAuthType authType,
            String authHeaderName,
            String authValue,
            boolean clearAuthSecret
    ) {
        if (customHeaders != null) {
            service.setCustomHeadersEncrypted(encryptHeaders(customHeaders));
        }
        applyAuth(service, authType, authHeaderName, authValue, clearAuthSecret);
    }

    public void applyTo(MonitoredService service, HttpHeaders headers) {
        decryptHeaders(service.getCustomHeadersEncrypted()).forEach(headers::set);
        if (service.getAuthType() == RequestAuthType.BEARER) {
            headers.setBearerAuth(requiredDecryptedAuthValue(service));
        } else if (service.getAuthType() == RequestAuthType.API_KEY) {
            headers.set(service.getAuthHeaderName(), requiredDecryptedAuthValue(service));
        }
    }

    public List<String> customHeaderNames(MonitoredService service) {
        return decryptHeaders(service.getCustomHeadersEncrypted()).keySet().stream().toList();
    }

    public boolean hasAuthSecret(MonitoredService service) {
        return service.getAuthValueEncrypted() != null;
    }

    private void applyAuth(
            MonitoredService service,
            RequestAuthType requestedType,
            String authHeaderName,
            String authValue,
            boolean clearAuthSecret
    ) {
        RequestAuthType authType = requestedType == null ? RequestAuthType.NONE : requestedType;
        if (clearAuthSecret) {
            service.setAuthType(RequestAuthType.NONE);
            service.setAuthHeaderName(null);
            service.setAuthValueEncrypted(null);
            return;
        }
        service.setAuthType(authType);

        if (authType == RequestAuthType.NONE) {
            service.setAuthHeaderName(null);
            service.setAuthValueEncrypted(null);
            return;
        }

        if (authType == RequestAuthType.BEARER) {
            service.setAuthHeaderName(HttpHeaders.AUTHORIZATION);
        } else {
            service.setAuthHeaderName(validateHeaderName(authHeaderName, "API key header name"));
        }

        if (authValue != null && !authValue.isBlank()) {
            validateHeaderValue(authValue, "Authentication value");
            service.setAuthValueEncrypted(encryptionService.encrypt(authValue));
        }

        if (service.getAuthValueEncrypted() == null) {
            throw new BadRequestException("Authentication value is required for " + authType);
        }
    }

    private String encryptHeaders(Map<String, String> headers) {
        Map<String, String> normalized = normalizeHeaders(headers);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return encryptionService.encrypt(objectMapper.writeValueAsString(normalized));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize custom headers", exception);
        }
    }

    private Map<String, String> decryptHeaders(String encryptedHeaders) {
        if (encryptedHeaders == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(
                    encryptionService.decrypt(encryptedHeaders),
                    new TypeReference<LinkedHashMap<String, String>>() {
                    }
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read encrypted custom headers", exception);
        }
    }

    private Map<String, String> normalizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        if (headers.size() > MAX_HEADERS) {
            throw new BadRequestException("A maximum of " + MAX_HEADERS + " custom headers is allowed");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        headers.forEach((name, value) -> {
            String validName = validateHeaderName(name, "Custom header name");
            validateHeaderValue(value, "Custom header value for " + validName);
            normalized.put(validName, value);
        });
        return normalized;
    }

    private String validateHeaderName(String name, String label) {
        if (name == null || name.isBlank() || !HEADER_NAME.matcher(name.trim()).matches()) {
            throw new BadRequestException(label + " is invalid");
        }
        String trimmed = name.trim();
        if (RESTRICTED_HEADERS.contains(trimmed.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException(trimmed + " cannot be configured as a request header");
        }
        return trimmed;
    }

    private void validateHeaderValue(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(label + " cannot be blank");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new BadRequestException(label + " exceeds " + MAX_VALUE_LENGTH + " characters");
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new BadRequestException(label + " cannot contain line breaks");
        }
    }

    private String requiredDecryptedAuthValue(MonitoredService service) {
        if (service.getAuthValueEncrypted() == null) {
            throw new IllegalStateException("Authentication secret is not configured");
        }
        return encryptionService.decrypt(service.getAuthValueEncrypted());
    }
}
