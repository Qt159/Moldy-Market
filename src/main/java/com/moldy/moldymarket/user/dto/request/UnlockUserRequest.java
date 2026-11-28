package com.moldy.moldymarket.user.dto.request;

import jakarta.validation.constraints.Size;

public record UnlockUserRequest(
        @Size(max = 500, message = "Note must not exceed 500 characters")
        String note
) {
}
