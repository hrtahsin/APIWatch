package com.hasan.apiwatch.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Component
public class ProductionSecurityValidator implements InitializingBean {

    private static final String DEVELOPMENT_ENCRYPTION_KEY =
            "YXBpd2F0Y2gtZGV2LWVuY3J5cHRpb24ta2V5LTMyYiE=";
    private static final int MINIMUM_PASSWORD_LENGTH = 12;
    private static final Set<String> UNSAFE_PASSWORDS = Set.of(
            "admin-change-me",
            "viewer-change-me",
            "replace-admin-password",
            "replace-viewer-password"
    );

    private final boolean requireSecureBootstrap;
    private final String encryptionKey;
    private final String adminUsername;
    private final String adminPassword;
    private final String viewerUsername;
    private final String viewerPassword;
    private final String frontendOrigin;
    private final boolean demoDataEnabled;
    private final boolean allowLocalhostCors;

    public ProductionSecurityValidator(
            @Value("${apiwatch.security.require-secure-bootstrap:false}") boolean requireSecureBootstrap,
            @Value("${apiwatch.secrets.encryption-key}") String encryptionKey,
            @Value("${apiwatch.auth.admin.username}") String adminUsername,
            @Value("${apiwatch.auth.admin.password}") String adminPassword,
            @Value("${apiwatch.auth.viewer.username}") String viewerUsername,
            @Value("${apiwatch.auth.viewer.password}") String viewerPassword,
            @Value("${apiwatch.frontend-origin}") String frontendOrigin,
            @Value("${apiwatch.demo-data.enabled:false}") boolean demoDataEnabled,
            @Value("${apiwatch.security.allow-localhost-cors:false}") boolean allowLocalhostCors
    ) {
        this.requireSecureBootstrap = requireSecureBootstrap;
        this.encryptionKey = encryptionKey;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.viewerUsername = viewerUsername;
        this.viewerPassword = viewerPassword;
        this.frontendOrigin = frontendOrigin;
        this.demoDataEnabled = demoDataEnabled;
        this.allowLocalhostCors = allowLocalhostCors;
    }

    @Override
    public void afterPropertiesSet() {
        if (!requireSecureBootstrap) {
            return;
        }

        List<String> violations = new ArrayList<>();
        if (!isValidEncryptionKey(encryptionKey)) {
            violations.add("APIWATCH_ENCRYPTION_KEY must be a 32-byte Base64 value");
        } else if (DEVELOPMENT_ENCRYPTION_KEY.equals(encryptionKey.trim())) {
            violations.add("APIWATCH_ENCRYPTION_KEY must not use the development value");
        }

        validatePrincipal("administrator", adminUsername, adminPassword, violations);
        validatePrincipal("viewer", viewerUsername, viewerPassword, violations);

        if (hasText(adminUsername) && hasText(viewerUsername)
                && adminUsername.trim().equalsIgnoreCase(viewerUsername.trim())) {
            violations.add("administrator and viewer usernames must be different");
        }
        if (hasText(adminPassword) && adminPassword.equals(viewerPassword)) {
            violations.add("administrator and viewer passwords must be different");
        }
        if (!isSecureFrontendOrigin(frontendOrigin)) {
            violations.add("APIWATCH_FRONTEND_ORIGIN must be an absolute HTTPS origin");
        }
        if (demoDataEnabled) {
            violations.add("APIWATCH_DEMO_DATA_ENABLED must be false");
        }
        if (allowLocalhostCors) {
            violations.add("APIWATCH_ALLOW_LOCALHOST_CORS must be false");
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Unsafe production configuration: " + String.join("; ", violations)
            );
        }
    }

    private void validatePrincipal(
            String label,
            String username,
            String password,
            List<String> violations
    ) {
        if (!hasText(username)) {
            violations.add(label + " username must not be blank");
        }
        if (!hasText(password) || password.length() < MINIMUM_PASSWORD_LENGTH) {
            violations.add(label + " password must contain at least "
                    + MINIMUM_PASSWORD_LENGTH + " characters");
        } else if (UNSAFE_PASSWORDS.contains(password)) {
            violations.add(label + " password must not use a documented placeholder");
        }
    }

    private boolean isSecureFrontendOrigin(String origin) {
        if (!hasText(origin) || origin.contains("*")) {
            return false;
        }
        try {
            URI uri = URI.create(origin.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getPath().isEmpty()
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isValidEncryptionKey(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(value.trim()).length == 32;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
