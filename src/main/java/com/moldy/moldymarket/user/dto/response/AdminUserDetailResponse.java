package com.moldy.moldymarket.user.dto.response;

import com.moldy.moldymarket.user.entity.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserDetailResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        String address,
        UserStatus status,
        Integer trustPoints,
        List<String> roles,
        Instant createdAt,
        Instant updatedAt,
        LockInfo lock,          // null if users aren't blocked
        OwnedStore ownedStore,  // null if users don't own store
        long openOrdersAsBuyer,
        long openOrdersAsSeller,
        boolean canLock,
        boolean canUnlock
) {


}
