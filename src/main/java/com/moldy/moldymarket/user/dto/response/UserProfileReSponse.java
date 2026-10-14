package com.moldy.moldymarket.user.dto.response;

import com.moldy.moldymarket.user.entity.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserProfileReSponse (
   UUID id,
   String email,
   String phone,
   String avatarUrl,
   UserStatus status,
   Integer trustPoints,
   List<String> roles,
   Instant createdAt
) {}
