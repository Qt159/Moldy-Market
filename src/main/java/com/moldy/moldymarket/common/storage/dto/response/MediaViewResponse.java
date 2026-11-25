package com.moldy.moldymarket.common.storage.dto.response;

import java.util.UUID;

public record MediaViewResponse(
        UUID id,
        String mediaType,
        String contentType,
        String fileName,
        String viewUrl,
        long expiresIn
) {}
