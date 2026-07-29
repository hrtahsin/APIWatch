package com.hasan.apiwatch.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityValidatorTest {

    private static final String DEVELOPMENT_KEY =
            "YXBpd2F0Y2gtZGV2LWVuY3J5cHRpb24ta2V5LTMyYiE=";
    private static final String PRODUCTION_KEY =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void acceptsSecureProductionConfiguration() {
        var validator = validator(
                true,
                PRODUCTION_KEY,
                "production-admin",
                "strong-admin-password",
                "production-viewer",
                "strong-viewer-password",
                "https://apiwatch.example",
                false,
                false
        );

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void rejectsDevelopmentDefaultsInSecureMode() {
        var validator = validator(
                true,
                DEVELOPMENT_KEY,
                "admin",
                "admin-change-me",
                "viewer",
                "viewer-change-me",
                "http://localhost:5173",
                true,
                true
        );

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APIWATCH_ENCRYPTION_KEY")
                .hasMessageContaining("administrator password")
                .hasMessageContaining("viewer password")
                .hasMessageContaining("absolute HTTPS origin")
                .hasMessageContaining("APIWATCH_DEMO_DATA_ENABLED")
                .hasMessageContaining("APIWATCH_ALLOW_LOCALHOST_CORS");
    }

    @Test
    void rejectsSharedBootstrapIdentityAndPassword() {
        var validator = validator(
                true,
                PRODUCTION_KEY,
                "same-user",
                "same-strong-password",
                "SAME-USER",
                "same-strong-password",
                "https://apiwatch.example",
                false,
                false
        );

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("usernames must be different")
                .hasMessageContaining("passwords must be different");
    }

    @Test
    void rejectsMalformedEncryptionKeys() {
        var validator = validator(
                true,
                "not-base64",
                "production-admin",
                "strong-admin-password",
                "production-viewer",
                "strong-viewer-password",
                "https://apiwatch.example",
                false,
                false
        );

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32-byte Base64 value");
    }

    @Test
    void doesNotEnforceProductionRulesOutsideSecureMode() {
        var validator = validator(
                false,
                DEVELOPMENT_KEY,
                "admin",
                "admin-change-me",
                "viewer",
                "viewer-change-me",
                "http://localhost:5173",
                true,
                true
        );

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    private ProductionSecurityValidator validator(
            boolean requireSecureBootstrap,
            String encryptionKey,
            String adminUsername,
            String adminPassword,
            String viewerUsername,
            String viewerPassword,
            String frontendOrigin,
            boolean demoDataEnabled,
            boolean allowLocalhostCors
    ) {
        return new ProductionSecurityValidator(
                requireSecureBootstrap,
                encryptionKey,
                adminUsername,
                adminPassword,
                viewerUsername,
                viewerPassword,
                frontendOrigin,
                demoDataEnabled,
                allowLocalhostCors
        );
    }
}
