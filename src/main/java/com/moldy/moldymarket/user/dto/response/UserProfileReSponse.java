package com.moldy.moldymarket.user.dto.response;

import com.moldy.moldymarket.user.entity.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserProfileReSponse (
   UUID id,
   String email,
   String fullName,
   String phone,
   String avatarUrl,
   String address,
   UserStatus status,
   Integer trustPoints,
   List<String> roles,
   List<String> permissions,
   UUID storeId,
   Instant createdAt
) {
    public UserProfileReSponse(
            UUID id,
            String email,
            String fullName,
            String phone,
            String avatarUrl,
            UserStatus status,
            Integer trustPoints,
            List<String> roles,
            List<String> permissions,
            UUID storeId,
            Instant createdAt
    ) {
        this(id, email, fullName, phone, avatarUrl, null, status, trustPoints, roles, permissions, storeId, createdAt);
    }
}
