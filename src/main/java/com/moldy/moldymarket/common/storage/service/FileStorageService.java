package com.moldy.moldymarket.common.storage.service;

import com.moldy.moldymarket.common.storage.dto.request.PresignedUrlRequest;
import com.moldy.moldymarket.common.storage.dto.response.DownloadUrlResponse;
import com.moldy.moldymarket.common.storage.dto.response.FileResponse;
import com.moldy.moldymarket.common.storage.dto.response.PresignedUrlResponse;
import com.moldy.moldymarket.common.storage.dto.response.MediaViewResponse;
import com.moldy.moldymarket.common.storage.enums.FilePurpose;

import java.util.List;
import java.util.UUID;

public interface FileStorageService {

    PresignedUrlResponse createUploadUrl(UUID ownerId, PresignedUrlRequest request);

    FileResponse confirmUpload(UUID fileId, UUID ownerId);

    DownloadUrlResponse generateDownloadUrl(UUID fileId, UUID requesterId);

    String generateAvatarViewUrl(UUID fileId);

    void validateAndLinkFiles(List<UUID> fileIds, UUID ownerId, FilePurpose purpose);

    void unlinkFiles(List<UUID> fileIds);

    List<MediaViewResponse> getMediaViews(List<UUID> fileIds);

    void deleteFile(UUID fileId, UUID requesterId);
}
