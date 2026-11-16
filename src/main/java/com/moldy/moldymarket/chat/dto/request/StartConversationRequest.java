package com.moldy.moldymarket.chat.dto.request;


import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartConversationRequest(
        @NotNull(message = "Target user ID cannot be null")
        UUID targetUserId
) {
}
