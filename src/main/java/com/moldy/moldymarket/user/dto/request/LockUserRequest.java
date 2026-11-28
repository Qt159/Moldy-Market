package com.moldy.moldymarket.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LockUserRequest(
        @NotBlank(message = "Lock reason is required")
        @Size(min = 10, max = 500, message = "Lock reason must be between 10 and 500 characters")
        String reason
) {
}
