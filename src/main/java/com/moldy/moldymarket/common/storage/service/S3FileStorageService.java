package com.moldy.moldymarket.common.storage.service;

import com.moldy.moldymarket.common.storage.config.S3Properties;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.storage.dto.request.PresignedUrlRequest;
import com.moldy.moldymarket.common.storage.dto.response.DownloadUrlResponse;
import com.moldy.moldymarket.common.storage.dto.response.FileResponse;
import com.moldy.moldymarket.common.storage.dto.response.PresignedUrlResponse;
import com.moldy.moldymarket.common.storage.dto.response.MediaViewResponse;
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
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3FileStorageService implements FileStorageService {

    private static final long MB = 1024L * 1024L;

    private static final long MAX_IMAGE_SIZE = 5 * MB;
    private static final long MAX_PDF_SIZE = 20 * MB;
    private static final long MAX_VIDEO_SIZE = 50 * MB;

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "video/mp4", ".mp4",
            "video/webm", ".webm",
            "application/pdf", ".pdf");

    private static final Set<FilePurpose> IMAGE_ONLY_PURPOSES = Set.of(
            FilePurpose.USER_AVATAR);

    private static final Set<FilePurpose> MEDIA_PURPOSES = Set.of(
            FilePurpose.PRODUCT_IMAGE,
            FilePurpose.DISPUTE_EVIDENCE,
            FilePurpose.APPRAISAL_EVIDENCE,
            FilePurpose.APPRAISAL_REPORT);

    private static final Set<FilePurpose> PDF_PURPOSES = Set.of(
            FilePurpose.ORDER_DOCUMENT,
            FilePurpose.APPRAISAL_EVIDENCE,
            FilePurpose.APPRAISAL_REPORT);

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

        final String uploadUrl;
        try {
            uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
        } catch (SdkException exception) {
            log.error("S3 upload URL signing failed. fileId={}, exceptionType={}",
                    storedFile.getId(), exception.getClass().getSimpleName());
            throw new AppException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
        }

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
            if (isMissingObject(exception)) {
                throw new AppException(ErrorCode.FILE_NOT_UPLOADED);
            }
            log.error("S3 object verification failed. fileId={}, awsStatus={}",
                    fileId, exception.statusCode());
            throw new AppException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
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
                .key(storedFile.getObjectKey())
                .responseContentType(storedFile.getContentType());
        if ("application/pdf".equals(storedFile.getContentType())) {
            getRequest.responseContentDisposition("attachment; filename=\""
                    + storedFile.getOriginalFilename().replace("\"", "") + "\"");
        } else {
            getRequest.responseContentDisposition("inline");
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

        String downloadUrl = presignGetObject(presignRequest, fileId);

        return DownloadUrlResponse.builder()
                .downloadUrl(downloadUrl)
                .expiresIn(expiration.toSeconds())
                .build();
    }

    /** Stable profile URL target; the returned S3 URL is short lived and never persisted. */
    @Transactional(readOnly = true)
    @Override
    public String generateAvatarViewUrl(UUID fileId) {
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if (file.getPurpose() != FilePurpose.USER_AVATAR) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        fileAuthorizationService.requireAvailable(file);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3Properties.getBucketName()).key(file.getObjectKey())
                .responseContentType(file.getContentType())
                .responseContentDisposition("inline").build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(s3Properties.getDownloadExpirationMinutes()))
                .getObjectRequest(getObjectRequest).build();
        return presignGetObject(presignRequest, fileId);
    }

    @Override
    @Transactional
    public void validateAndLinkFiles(List<UUID> fileIds, UUID ownerId, FilePurpose purpose) {
        if (fileIds == null || fileIds.isEmpty()) return;
        if (fileIds.stream().anyMatch(java.util.Objects::isNull)
                || fileIds.stream().distinct().count() != fileIds.size()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Media file IDs must be unique and non-null");
        }
        List<UUID> sortedIds = fileIds.stream().sorted().toList();
        List<StoredFile> files = storedFileRepository.findAllByIdForUpdate(sortedIds);
        if (files.size() != fileIds.size()) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        Map<UUID, StoredFile> byId = files.stream().collect(java.util.stream.Collectors.toMap(StoredFile::getId, f -> f));
        for (UUID id : fileIds) {
            StoredFile file = byId.get(id);
            fileAuthorizationService.requireOwner(file, ownerId);
            fileAuthorizationService.requirePurpose(file, purpose);
            fileAuthorizationService.requireAvailable(file);
            if (file.getLinkedAt() != null) {
                throw new AppException(ErrorCode.FILE_NOT_AVAILABLE, "File is already linked to a resource");
            }
        }
        LocalDateTime linkedAt = LocalDateTime.now();
        files.forEach(file -> file.setLinkedAt(linkedAt));
        storedFileRepository.saveAll(files);
    }

    @Override
    @Transactional
    public void unlinkFiles(List<UUID> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) return;
        List<StoredFile> files = storedFileRepository.findAllByIdForUpdate(fileIds.stream().sorted().toList());
        files.forEach(file -> file.setLinkedAt(null));
        storedFileRepository.saveAll(files);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaViewResponse> getMediaViews(List<UUID> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) return List.of();
        List<StoredFile> files = storedFileRepository.findAllById(fileIds);
        Map<UUID, StoredFile> byId = files.stream().collect(java.util.stream.Collectors.toMap(StoredFile::getId, f -> f));
        List<MediaViewResponse> result = new ArrayList<>(fileIds.size());
        for (UUID id : fileIds) {
            StoredFile file = byId.get(id);
            if (file == null || file.getStatus() != FileStatus.AVAILABLE || file.getLinkedAt() == null) continue;
            GetObjectRequest.Builder getRequest = GetObjectRequest.builder()
                    .bucket(s3Properties.getBucketName()).key(file.getObjectKey())
                    .responseContentType(file.getContentType());
            if ("application/pdf".equals(file.getContentType())) {
                getRequest.responseContentDisposition("attachment; filename=\""
                        + file.getOriginalFilename().replace("\"", "") + "\"");
            } else {
                getRequest.responseContentDisposition("inline");
            }
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(s3Properties.getDownloadExpirationMinutes()))
                    .getObjectRequest(getRequest.build()).build();
            String mediaType = file.getContentType().startsWith("video/") ? "VIDEO"
                    : file.getContentType().equals("application/pdf") ? "DOCUMENT" : "IMAGE";
            result.add(new MediaViewResponse(file.getId(), mediaType, file.getContentType(),
                    file.getOriginalFilename(), presignGetObject(presignRequest, file.getId()),
                    Duration.ofMinutes(s3Properties.getDownloadExpirationMinutes()).toSeconds()));
        }
        return result;
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
            storedFileStateService.deletionFailed(fileId);
            log.warn("Stored file deletion deferred for retry. fileId={}, awsStatus={}",
                    fileId, exception instanceof S3Exception s3Exception ? s3Exception.statusCode() : null);
            throw new AppException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
        }
    }

    static boolean isMissingObject(S3Exception exception) {
        String errorCode = exception.awsErrorDetails() == null
                ? null : exception.awsErrorDetails().errorCode();
        return "NoSuchKey".equals(errorCode)
                || "NoSuchObject".equals(errorCode)
                || "NotFound".equals(errorCode);
    }

    private String presignGetObject(GetObjectPresignRequest request, UUID fileId) {
        try {
            return s3Presigner.presignGetObject(request).url().toString();
        } catch (SdkException exception) {
            log.error("S3 download URL signing failed. fileId={}, exceptionType={}",
                    fileId, exception.getClass().getSimpleName());
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

        String extension = filenameExtension(request.getFilename());
        Set<String> expectedExtensions = switch (contentType) {
            case "image/jpeg" -> Set.of("jpg", "jpeg");
            case "image/png" -> Set.of("png");
            case "image/webp" -> Set.of("webp");
            case "video/mp4" -> Set.of("mp4");
            case "video/webm" -> Set.of("webm");
            case "application/pdf" -> Set.of("pdf");
            default -> Set.of();
        };
        if (!expectedExtensions.contains(extension)) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Filename extension does not match content type");
        }

        boolean isImage = contentType.startsWith("image/");
        boolean isVideo = contentType.startsWith("video/");
        boolean isPdf = "application/pdf".equals(contentType);

        if (IMAGE_ONLY_PURPOSES.contains(request.getPurpose()) && !isImage) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "This file purpose only accepts images");
        }
        if (isVideo && !MEDIA_PURPOSES.contains(request.getPurpose())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "This file purpose does not accept videos");
        }
        if (MEDIA_PURPOSES.contains(request.getPurpose()) && !isImage && !isVideo
                && !(isPdf && PDF_PURPOSES.contains(request.getPurpose()))) {
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

    private String filenameExtension(String filename) {
        String sanitized = filename.replace("\\", "/");
        sanitized = sanitized.substring(sanitized.lastIndexOf('/') + 1);
        int dot = sanitized.lastIndexOf('.');
        return dot < 0 ? "" : sanitized.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        return contentType == null
                ? ""
                : contentType.trim().toLowerCase(Locale.ROOT);
    }

}
