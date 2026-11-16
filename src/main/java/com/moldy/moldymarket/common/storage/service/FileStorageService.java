package com.moldy.moldymarket.common.storage.service;

import com.moldy.moldymarket.common.storage.dto.request.PresignedUrlRequest;
import com.moldy.moldymarket.common.storage.dto.response.DownloadUrlResponse;
import com.moldy.moldymarket.common.storage.dto.response.FileResponse;
import com.moldy.moldymarket.common.storage.dto.response.PresignedUrlResponse;

import java.util.UUID;

public interface FileStorageService {

    PresignedUrlResponse createUploadUrl(UUID ownerId, PresignedUrlRequest request);

    FileResponse confirmUpload(UUID fileId, UUID ownerId);

    DownloadUrlResponse generateDownloadUrl(UUID fileId, UUID requesterId);

    void deleteFile(UUID fileId, UUID requesterId);
}