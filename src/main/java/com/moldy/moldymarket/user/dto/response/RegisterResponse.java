package com.moldy.moldymarket.user.dto.response;

import java.util.UUID;

public record RegisterResponse (
        UUID userId,
        String email,
        String fullName,
        String status
) {}
