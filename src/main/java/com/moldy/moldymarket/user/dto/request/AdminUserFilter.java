package com.moldy.moldymarket.user.dto.request;

import com.moldy.moldymarket.user.entity.UserStatus;

public record AdminUserFilter(
        String keyword,
        UserStatus status,
        String role
) {
}
