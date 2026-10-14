package com.moldy.moldymarket.user.dto.request;

import com.moldy.moldymarket.user.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(

        @NotNull(message = "Status is required")
        UserStatus status
) {
}
