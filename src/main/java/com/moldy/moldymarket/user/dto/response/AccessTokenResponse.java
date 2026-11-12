package com.moldy.moldymarket.user.dto.response;

import java.util.UUID;

public record AccessTokenResponse(
        UUID userId,
        String email,
        String fullName,
        String accessToken,
        String tokenType
) {
}
