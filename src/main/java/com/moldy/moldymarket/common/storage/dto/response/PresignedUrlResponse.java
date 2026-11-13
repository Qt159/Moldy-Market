package com.moldy.moldymarket.common.storage.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record PresignedUrlResponse(
        UUID fileId,
        String uploadUrl,
        String objectKey,
        long expiresIn
) {
}