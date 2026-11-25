package com.moldy.moldymarket.common.storage.dto.response;

import lombok.Builder;

@Builder
public record DownloadUrlResponse(
    String downloadUrl,
    long expiresIn,
    String publicUrl
) {
    public DownloadUrlResponse(String downloadUrl, long expiresIn) {
        this(downloadUrl, expiresIn, null);
    }
}