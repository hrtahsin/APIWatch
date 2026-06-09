package com.hasan.apiwatch.dto;

import com.hasan.apiwatch.enums.HttpMethodType;
import com.hasan.apiwatch.enums.RequestAuthType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.Map;

public record UpdateServiceRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank
        @URL
        @Pattern(regexp = "(?i)^https?://.*$", message = "must use HTTP or HTTPS")
        String url,
        @NotNull HttpMethodType method,
        @Min(100) @Max(599) Integer expectedStatusCode,
        @Min(100) @Max(599) Integer expectedStatusMin,
        @Min(100) @Max(599) Integer expectedStatusMax,
        @NotNull @Min(100) @Max(120_000) Integer timeoutMs,
        @NotNull @Min(10) @Max(86_400) Integer checkIntervalSeconds,
        @Size(max = 500) String responseBodyContains,
        @NotNull @Min(1) @Max(20) Integer failureThreshold,
        @NotNull Boolean active,
        Map<String, String> customHeaders,
        @NotNull RequestAuthType authType,
        @Size(max = 120) String authHeaderName,
        @Size(max = 4096) String authValue,
        Boolean clearAuthSecret
) {
}
