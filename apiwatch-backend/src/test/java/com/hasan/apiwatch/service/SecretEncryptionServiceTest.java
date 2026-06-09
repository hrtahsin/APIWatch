package com.hasan.apiwatch.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretEncryptionServiceTest {

    private static final String KEY =
            "YXBpd2F0Y2gtZGV2LWVuY3J5cHRpb24ta2V5LTMyYiE=";

    @Test
    void encryptsAndDecryptsSecretsWithUniqueCiphertext() {
        SecretEncryptionService encryptionService = new SecretEncryptionService(KEY);

        String first = encryptionService.encrypt("github-token");
        String second = encryptionService.encrypt("github-token");

        assertThat(first).startsWith("v1:").isNotEqualTo(second);
        assertThat(encryptionService.decrypt(first)).isEqualTo("github-token");
        assertThat(encryptionService.decrypt(second)).isEqualTo("github-token");
    }

    @Test
    void rejectsKeysThatAreNotThirtyTwoBytes() {
        assertThatThrownBy(() -> new SecretEncryptionService("dG9vLXNob3J0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
