package com.moldy.moldymarket.common.storage.dto.response;

import com.moldy.moldymarket.common.storage.enums.FilePurpose;
import com.moldy.moldymarket.common.storage.enums.FileStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record FileResponse(
    UUID fileId,
    String originalFilename,
    String contentType,
    Long fileSize,
    FilePurpose purpose,
    FileStatus status,
    LocalDateTime uploadedAt
) {
}