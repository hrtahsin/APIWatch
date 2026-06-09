package com.hasan.apiwatch.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateServiceActiveRequest(
        @NotNull Boolean active
) {
}
