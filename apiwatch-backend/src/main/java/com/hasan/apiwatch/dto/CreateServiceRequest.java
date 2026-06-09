package com.hasan.apiwatch.dto;

import com.hasan.apiwatch.enums.HttpMethodType;
import com.hasan.apiwatch.enums.RequestAuthType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.Map;

public record CreateServiceRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank
        @URL
        @Pattern(regexp = "(?i)^https?://.*$", message = "must use HTTP or HTTPS")
        String url,
        HttpMethodType method,
        @Min(100) @Max(599) Integer expectedStatusCode,
        @Min(100) @Max(120_000) Integer timeoutMs,
        @Min(1) @Max(20) Integer failureThreshold,
        Boolean active,
        Map<String, String> customHeaders,
        RequestAuthType authType,
        @Size(max = 120) String authHeaderName,
        @Size(max = 4096) String authValue
) {
}
