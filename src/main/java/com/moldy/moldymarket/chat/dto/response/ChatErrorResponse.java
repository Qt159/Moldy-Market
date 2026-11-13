package com.moldy.moldymarket.chat.dto.response;

import lombok.Builder;

import java.time.Instant;

@Builder
public record ChatErrorResponse(
        String code,
        String errorMessage,
        Instant timestamp
) {
}
