package com.hasan.apiwatch.service;

import com.hasan.apiwatch.exception.BadRequestException;
import com.hasan.apiwatch.exception.UnsafeTargetException;
import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlSafetyServiceTest {

    private final UrlSafetyService service = new UrlSafetyService(true, "");

    @Test
    void rejectsLoopbackAndPrivateTargets() {
        assertThatThrownBy(() -> service.validateConfiguration("http://127.0.0.1:8080/health"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("private or internal");
        assertThatThrownBy(() -> service.validateConfiguration("http://10.0.0.5/health"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void allowsExplicitlyAllowlistedInternalHost() throws UnknownHostException {
        UrlSafetyService allowlisted = new UrlSafetyService(true, "localhost");

        assertThat(allowlisted.validateConfiguration("http://localhost:8080/health"))
                .isEqualTo("http://localhost:8080/health");
        allowlisted.assertRequestAllowed("http://localhost:8080/health");
    }

    @Test
    void blocksPrivateTargetAgainAtRequestTime() {
        assertThatThrownBy(() -> service.assertRequestAllowed("http://169.254.169.254/latest"))
                .isInstanceOf(UnsafeTargetException.class);
    }
}
