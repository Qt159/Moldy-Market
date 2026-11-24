package com.moldy.moldymarket.common.storage.service;

import com.moldy.moldymarket.common.storage.config.S3Properties;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
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
import com.moldy.moldymarket.common.storage.service.FileAuthorizationService;
import com.moldy.moldymarket.common.storage.service.FileContentValidator.InvalidFileContentException;
import com.moldy.moldymarket.common.storage.service.StoredFileStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
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
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3FileStorageService implements FileStorageService {

    private static final long MB = 1024L * 1024L;

    private static final long MAX_IMAGE_SIZE = 10 * MB;
    private static final long MAX_PDF_SIZE = 20 * MB;
    private static final long MAX_VIDEO_SIZE = 50 * MB;

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "application/pdf", ".pdf");

    private static final Set<FilePurpose> IMAGE_ONLY_PURPOSES = Set.of(
            FilePurpose.PRODUCT_IMAGE, FilePurpose.USER_AVATAR,
            FilePurpose.DISPUTE_EVIDENCE);

    private static final Set<FilePurpose> MEDIA_PURPOSES = Set.of(
            FilePurpose.PRODUCT_IMAGE,
            FilePurpose.DISPUTE_EVIDENCE);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
    private final StoredFileRepository storedFileRepository;
    private final StoredFileMapper storedFileMapper;
    private final FileAuthorizationService fileAuthorizationService;
    private final FileContentValidator fileContentValidator;
    private final StoredFileStateService storedFileStateService;

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
        log.info("Upload URL requested. fileId={}, purpose={}, ownerId={}",
                storedFile.getId(), storedFile.getPurpose(), ownerId);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getBucketName())
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
    public FileResponse confirmUpload(UUID fileId, UUID ownerId) {
        StoredFile storedFile = storedFileRepository.findById(fileId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        fileAuthorizationService.requireOwner(storedFile, ownerId);

        if (storedFile.getStatus() == FileStatus.AVAILABLE) {
            return storedFileMapper.toResponse(storedFile);
        }

        if (storedFile.getStatus() != FileStatus.PENDING) {
            throw new AppException(ErrorCode.FILE_NOT_AVAILABLE);
        }

        HeadObjectResponse headObject;

        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(storedFile.getObjectKey())
                    .build();

            headObject = s3Client.headObject(headRequest);

        } catch (NoSuchKeyException exception) {
            throw new AppException(ErrorCode.FILE_NOT_UPLOADED);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new AppException(ErrorCode.FILE_NOT_UPLOADED);
            }
            log.error("S3 object verification failed. fileId={}, awsStatus={}",
                    fileId, exception.statusCode());
            if (exception.statusCode() >= 500) {
                throw new AppException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
            }
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        } catch (SdkException exception) {
            log.error("S3 object verification failed. fileId={}, exceptionType={}",
                    fileId, exception.getClass().getSimpleName());
            throw new AppException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
        }

        long actualSize = headObject.contentLength();
        String actualContentType = normalizeContentType(
                headObject.contentType()
        );

        boolean validSize = actualSize > 0
                && actualSize == storedFile.getExpectedFileSize();

        boolean validContentType = storedFile.getContentType()
                .equals(actualContentType);

        if (!validSize || !validContentType) {
            return completeUpload(storedFile, ownerId, actualSize, false,
                    "HEAD metadata mismatch");
        }

        try (InputStream content = s3Client.getObject(software.amazon.awssdk.services.s3.model.GetObjectRequest
                .builder().bucket(s3Properties.getBucketName()).key(storedFile.getObjectKey()).build())) {
            fileContentValidator.validate(actualContentType, content);
        } catch (InvalidFileContentException exception) {
            return completeUpload(storedFile, ownerId, actualSize, false,
                    "Content validation failed");
        } catch (S3Exception exception) {
            log.error("S3 object content read failed. fileId={}, awsStatus={}",
                    fileId, exception.statusCode());
            if (exception.statusCode() == 403) {
                throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            throw new AppException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
        } catch (SdkException exception) {
            log.error("S3 object content read failed. fileId={}, exceptionType={}",
                    fileId, exception.getClass().getSimpleName());
            throw new AppException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
        } catch (IOException exception) {
            log.error("S3 object content read failed. fileId={}, exceptionType={}",
                    fileId, exception.getClass().getSimpleName());
            throw new AppException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
        }

        return completeUpload(storedFile, ownerId, actualSize, true, null);
    }

    private FileResponse completeUpload(StoredFile file, UUID ownerId, long actualSize,
            boolean valid, String reason) {
        StoredFile completed = storedFileStateService.completeUploadVerification(
                file.getId(), ownerId, actualSize, valid);
        if (valid) {
            log.info("Stored file confirmed. fileId={}, purpose={}", completed.getId(), completed.getPurpose());
        } else {
            log.warn("Uploaded file rejected. fileId={}, purpose={}, reason={}",
                    completed.getId(), completed.getPurpose(), reason);
        }
        return storedFileMapper.toResponse(completed);
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadUrlResponse generateDownloadUrl(
            UUID fileId,
            UUID requesterId
    ) {
        StoredFile storedFile = fileAuthorizationService.requireOwner(fileId, requesterId);
        fileAuthorizationService.requireAvailable(storedFile);
        log.info("Download URL authorized. fileId={}, ownerId={}", fileId, requesterId);

        GetObjectRequest.Builder getRequest = GetObjectRequest.builder()
                .bucket(s3Properties.getBucketName())
                .key(storedFile.getObjectKey());
        if ("application/pdf".equals(storedFile.getContentType())) {
            getRequest.responseContentDisposition("attachment; filename=\""
                    + storedFile.getOriginalFilename().replace("\"", "") + "\"");
        }
        GetObjectRequest getObjectRequest = getRequest.build();

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
    public void deleteFile(UUID fileId, UUID requesterId) {
        StoredFile file = storedFileStateService.requestDeletion(fileId, requesterId).orElse(null);
        if (file == null || (file.getDeletionNotBefore() != null
                && file.getDeletionNotBefore().isAfter(LocalDateTime.now()))) return;
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.getBucketName()).key(file.getObjectKey()).build());
            storedFileStateService.deletionSucceeded(fileId);
            log.info("Stored file deleted. fileId={}, purpose={}", fileId, file.getPurpose());
        } catch (SdkException exception) {
            if (exception instanceof S3Exception s3Exception && s3Exception.statusCode() == 404) {
                storedFileStateService.deletionSucceeded(fileId);
                return;
            }
            storedFileStateService.deletionFailed(fileId);
            log.warn("Stored file deletion deferred for retry. fileId={}, awsStatus={}",
                    fileId, exception instanceof S3Exception s3Exception ? s3Exception.statusCode() : null);
            throw new AppException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
        }
    }

    private void validateFile(PresignedUrlRequest request) {
        if (request.getPurpose() == null || request.getFileSize() == null
                || request.getFileSize() <= 0 || request.getFilename() == null
                || request.getFilename().isBlank()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        String contentType = normalizeContentType(request.getContentType());

        if (!ALLOWED_CONTENT_TYPES.containsKey(contentType)) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Unsupported file content type");
        }

        boolean isImage = contentType.startsWith("image/");
        boolean isVideo = contentType.startsWith("video/");
        boolean isPdf = "application/pdf".equals(contentType);

        if (IMAGE_ONLY_PURPOSES.contains(request.getPurpose()) && !isImage) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "This file purpose only accepts images");
        }
        if (MEDIA_PURPOSES.contains(request.getPurpose()) && !isImage && !isVideo) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "This file purpose only accepts images or videos");
        }
        if (request.getPurpose() == FilePurpose.ORDER_DOCUMENT && !isPdf) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Order documents must be PDF files");
        }

        long maxSize;
        if (isImage) {
            maxSize = MAX_IMAGE_SIZE;
        } else if (isVideo) {
            maxSize = MAX_VIDEO_SIZE;
        } else {
            maxSize = MAX_PDF_SIZE;
        }

        if (request.getFileSize() > maxSize) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "File size exceeds the allowed limit");
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
        if (sanitized.isBlank()) sanitized = "file";

        return sanitized.length() > 255
                ? sanitized.substring(0, 255)
                : sanitized;
    }

    private String normalizeContentType(String contentType) {
        return contentType == null
                ? ""
                : contentType.trim().toLowerCase(Locale.ROOT);
    }

}
