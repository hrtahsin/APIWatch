package com.hasan.apiwatch.dto;

import com.hasan.apiwatch.enums.HttpMethodType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record UpdateServiceRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank
        @URL
        @Pattern(regexp = "(?i)^https?://.*$", message = "must use HTTP or HTTPS")
        String url,
        @NotNull HttpMethodType method,
        @NotNull @Min(100) @Max(599) Integer expectedStatusCode,
        @NotNull @Min(100) @Max(120_000) Integer timeoutMs,
        @NotNull @Min(1) @Max(20) Integer failureThreshold,
        @NotNull Boolean active
) {
}
