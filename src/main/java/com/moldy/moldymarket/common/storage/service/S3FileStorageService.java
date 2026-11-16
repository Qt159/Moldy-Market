package com.moldy.moldymarket.common.storage.service;

import com.moldy.moldymarket.common.storage.config.S3Properties;
import com.moldy.moldymarket.common.storage.dto.request.PresignedUrlRequest;
import com.moldy.moldymarket.common.storage.dto.response.DownloadUrlResponse;
import com.moldy.moldymarket.common.storage.dto.response.FileResponse;
import com.moldy.moldymarket.common.storage.dto.response.PresignedUrlResponse;
import com.moldy.moldymarket.common.storage.entity.StoredFile;
import com.moldy.moldymarket.common.storage.enums.FilePurpose;
import com.moldy.moldymarket.common.storage.enums.FileStatus;
import com.moldy.moldymarket.common.storage.mapper.StoredFileMapper;
import com.moldy.moldymarket.common.storage.repository.StoredFileRepository;
import com.moldy.moldymarket.common.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3FileStorageService implements FileStorageService {

    private static final long MB = 1024L * 1024L;

    private static final long MAX_IMAGE_SIZE = 10 * MB;
    private static final long MAX_PDF_SIZE = 20 * MB;

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "application/pdf", ".pdf");

    private static final Set<FilePurpose> IMAGE_ONLY_PURPOSES = Set.of(
            FilePurpose.PRODUCT_IMAGE, FilePurpose.USER_AVATAR);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
    private final StoredFileRepository storedFileRepository;
    private final StoredFileMapper storedFileMapper;

    @Override
    @Transactional
    public PresignedUrlResponse createUploadUrl(
            UUID ownerId, PresignedUrlRequest request) {
        validateFile(request);

        String contentType = normalizeContentType(request.getContentType());
        String extension = ALLOWED_CONTENT_TYPES.get(contentType);

        String objectKey = buildObjectKey(
                request.getPurpose(),
                ownerId,
                extension);

        StoredFile storedFile = StoredFile.builder()
                .ownerId(ownerId)
                .objectKey(objectKey)
                .originalFilename(sanitizeFilename(request.getFilename()))
                .contentType(contentType)
                .expectedFileSize(request.getFileSize())
                .purpose(request.getPurpose())
                .status(FileStatus.PENDING)
                .build();

        storedFile = storedFileRepository.save(storedFile);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .build();

        Duration expiration = Duration.ofMinutes(
                s3Properties.getUploadExpirationMinutes());

        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(expiration)
                        .putObjectRequest(putObjectRequest)
                        .build();

        String uploadUrl = s3Presigner.presignPutObject(presignRequest)
                .url()
                .toString();

        return PresignedUrlResponse.builder()
                .fileId(storedFile.getId())
                .uploadUrl(uploadUrl)
                .objectKey(objectKey)
                .expiresIn(expiration.toSeconds())
                .build();
    }

    @Override
    @Transactional
    public FileResponse confirmUpload(UUID fileId, UUID ownerId) {
        StoredFile storedFile = storedFileRepository
                .findByIdForUpdate(fileId)
                .orElseThrow(() -> notFound("File not found"));

        validateOwner(storedFile, ownerId);

        if (storedFile.getStatus() == FileStatus.AVAILABLE) {
            return storedFileMapper.toResponse(storedFile);
        }

        if (storedFile.getStatus() != FileStatus.PENDING) {
            throw badRequest("File cannot be confirmed in its current status");
        }

        HeadObjectResponse headObject;

        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(storedFile.getObjectKey())
                    .build();

            headObject = s3Client.headObject(headRequest);

        } catch (NoSuchKeyException exception) {
            throw badRequest("File has not been uploaded to S3");
        } catch (SdkException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to verify file on S3",
                    exception
            );
        }

        long actualSize = headObject.contentLength();
        String actualContentType = normalizeContentType(
                headObject.contentType()
        );

        storedFile.setActualFileSize(actualSize);

        boolean validSize = actualSize > 0
                && actualSize == storedFile.getExpectedFileSize();

        boolean validContentType = storedFile.getContentType()
                .equals(actualContentType);

        if (!validSize || !validContentType) {
            storedFile.setStatus(FileStatus.REJECTED);

            // Không throw sau khi save, vì exception có thể rollback transaction.
            // Trả trạng thái REJECTED để caller biết file không hợp lệ.
            return storedFileMapper.toResponse(
                    storedFileRepository.save(storedFile)
            );
        }

        storedFile.setStatus(FileStatus.AVAILABLE);
        storedFile.setUploadedAt(LocalDateTime.now());

        return storedFileMapper.toResponse(
                storedFileRepository.save(storedFile)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadUrlResponse generateDownloadUrl(
            UUID fileId,
            UUID requesterId
    ) {
        StoredFile storedFile = storedFileRepository
                .findByIdAndOwnerId(fileId, requesterId)
                .orElseThrow(() -> notFound("File not found"));

        if (storedFile.getStatus() != FileStatus.AVAILABLE) {
            throw badRequest("File is not available for download");
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(storedFile.getObjectKey())
                .build();

        Duration expiration = Duration.ofMinutes(
                s3Properties.getDownloadExpirationMinutes()
        );

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(expiration)
                        .getObjectRequest(getObjectRequest)
                        .build();

        String downloadUrl = s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();

        return DownloadUrlResponse.builder()
                .downloadUrl(downloadUrl)
                .expiresIn(expiration.toSeconds())
                .build();
    }

    @Override
    @Transactional
    public void deleteFile(UUID fileId, UUID requesterId) {
        StoredFile storedFile = storedFileRepository
                .findByIdForUpdate(fileId)
                .orElseThrow(() -> notFound("File not found"));

        validateOwner(storedFile, requesterId);

        if (storedFile.getStatus() == FileStatus.DELETED) {
            return;
        }

        if (storedFile.getLinkedAt() != null) {
            throw badRequest("Cannot delete a file linked to business data");
        }

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(storedFile.getObjectKey())
                    .build();

            s3Client.deleteObject(deleteRequest);

        } catch (SdkException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to delete file from S3",
                    exception
            );
        }

        storedFile.setStatus(FileStatus.DELETED);
        storedFileRepository.save(storedFile);
    }

    private void validateFile(PresignedUrlRequest request) {
        String contentType = normalizeContentType(request.getContentType());

        if (!ALLOWED_CONTENT_TYPES.containsKey(contentType)) {
            throw badRequest("Unsupported file content type");
        }

        if (IMAGE_ONLY_PURPOSES.contains(request.getPurpose())
                && !contentType.startsWith("image/")) {
            throw badRequest("This file purpose only accepts images");
        }

        long maxSize = contentType.startsWith("image/")
                ? MAX_IMAGE_SIZE
                : MAX_PDF_SIZE;

        if (request.getFileSize() > maxSize) {
            throw badRequest("File size exceeds the allowed limit");
        }
    }

    private void validateOwner(StoredFile storedFile, UUID ownerId) {
        if (!storedFile.getOwnerId().equals(ownerId)) {
            // Dùng 404 để tránh tiết lộ file của user khác.
            throw notFound("File not found");
        }
    }

    private String buildObjectKey(
            FilePurpose purpose,
            UUID ownerId,
            String extension
    ) {
        return purpose.name().toLowerCase(Locale.ROOT)
                + "/"
                + ownerId
                + "/"
                + UUID.randomUUID()
                + extension;
    }

    private String sanitizeFilename(String filename) {
        String sanitized = filename
                .replace("\\", "/");

        sanitized = sanitized.substring(
                sanitized.lastIndexOf('/') + 1
        );

        sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "_");

        return sanitized.length() > 255
                ? sanitized.substring(0, 255)
                : sanitized;
    }

    private String normalizeContentType(String contentType) {
        return contentType == null
                ? ""
                : contentType.trim().toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}