package com.moldy.moldymarket.user.dto;

import java.util.UUID;

public record AuthResponse (
        UUID userId,
        String email,
        String fullName,
        String accessToken,
        String refreshToken,
        String tokenType
) {}
