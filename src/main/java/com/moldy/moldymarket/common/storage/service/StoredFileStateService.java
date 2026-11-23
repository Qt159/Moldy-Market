package com.moldy.moldymarket.common.storage.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.storage.config.S3Properties;
import com.moldy.moldymarket.common.storage.entity.StoredFile;
import com.moldy.moldymarket.common.storage.enums.FileStatus;
import com.moldy.moldymarket.common.storage.repository.StoredFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Service
public class StoredFileStateService {

    public record DeletionBatch(UUID claimToken, List<StoredFile> files) {}

    private final StoredFileRepository repository;
    private final S3Properties properties;
    private final Duration retryInitialDelay;
    private final Duration retryMaxDelay;

    public StoredFileStateService(
            StoredFileRepository repository,
            S3Properties properties,
            @Value("${storage.cleanup.retry-initial-delay:5m}") Duration retryInitialDelay,
            @Value("${storage.cleanup.retry-max-delay:1h}") Duration retryMaxDelay) {
        if (retryInitialDelay.isNegative() || retryInitialDelay.isZero()
                || retryMaxDelay.compareTo(retryInitialDelay) < 0
                || retryInitialDelay.toSeconds() < 1 || retryMaxDelay.toSeconds() < 1) {
            throw new IllegalArgumentException("Invalid storage deletion retry configuration");
        }
        this.repository = repository;
        this.properties = properties;
        this.retryInitialDelay = retryInitialDelay;
        this.retryMaxDelay = retryMaxDelay;
    }

    @Transactional
    public Optional<StoredFile> requestDeletion(UUID fileId, UUID ownerId) {
        StoredFile file = repository.findByIdForUpdate(fileId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!file.getOwnerId().equals(ownerId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (file.getStatus() == FileStatus.DELETED) return Optional.empty();
        if (file.getLinkedAt() != null) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Cannot delete a file linked to business data");
        }
        if (file.getStatus() == FileStatus.DELETE_PENDING
                || (file.getStatus() == FileStatus.REJECTED && file.getDeletionRequestedAt() != null)) {
            return Optional.of(file);
        }

        LocalDateTime now = LocalDateTime.now();
        file.setDeletionRequestedAt(now);
        if (file.getStatus() == FileStatus.PENDING) {
            file.setStatus(FileStatus.DELETE_PENDING);
            // A still-valid presigned PUT cannot be revoked. Wait for it to expire before removing the key.
            file.setDeletionNotBefore(file.getCreatedAt().plusMinutes(properties.getUploadExpirationMinutes()));
        } else if (file.getStatus() == FileStatus.AVAILABLE) {
            file.setStatus(FileStatus.DELETE_PENDING);
            file.setDeletionNotBefore(now);
        } else if (file.getStatus() == FileStatus.REJECTED) {
            file.setDeletionNotBefore(now);
        } else {
            throw new AppException(ErrorCode.FILE_NOT_AVAILABLE);
        }
        return Optional.of(repository.save(file));
    }

    @Transactional
    public Optional<StoredFile> markExpiredPendingForDeletion(UUID fileId, LocalDateTime cutoff) {
        StoredFile file = repository.findByIdForUpdate(fileId).orElse(null);
        if (file == null || file.getStatus() != FileStatus.PENDING
                || file.getLinkedAt() != null || !file.getCreatedAt().isBefore(cutoff)) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        file.setStatus(FileStatus.DELETE_PENDING);
        file.setDeletionRequestedAt(now);
        file.setDeletionNotBefore(now);
        return Optional.of(repository.save(file));
    }

    /** Applies an S3 verification result while holding a short row lock. */
    @Transactional
    public StoredFile completeUploadVerification(UUID fileId, UUID ownerId, long actualSize,
            boolean valid) {
        StoredFile file = repository.findByIdForUpdate(fileId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!file.getOwnerId().equals(ownerId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (file.getStatus() == FileStatus.AVAILABLE) return file;
        if (file.getStatus() != FileStatus.PENDING || file.getLinkedAt() != null) {
            throw new AppException(ErrorCode.FILE_NOT_AVAILABLE);
        }
        file.setActualFileSize(actualSize);
        if (!valid) {
            LocalDateTime now = LocalDateTime.now();
            file.setStatus(FileStatus.REJECTED);
            file.setDeletionRequestedAt(now);
            file.setDeletionNotBefore(now);
        } else {
            file.setStatus(FileStatus.AVAILABLE);
            file.setUploadedAt(LocalDateTime.now());
        }
        return repository.save(file);
    }

    @Transactional
    public DeletionBatch claimDeletionBatch(LocalDateTime pendingCutoff, int batchSize) {
        UUID token = UUID.randomUUID();
        repository.claimDeletionBatch(pendingCutoff, batchSize, token,
                LocalDateTime.now().plusMinutes(5));
        return new DeletionBatch(token, repository.findAllByDeletionClaimToken(token));
    }

    @Transactional
    public void deletionSucceeded(UUID fileId, UUID claimToken) {
        repository.findByIdForUpdate(fileId).ifPresent(file -> {
            if (!claimToken.equals(file.getDeletionClaimToken()) || file.getLinkedAt() != null) return;
            if (file.getStatus() == FileStatus.DELETE_PENDING) file.setStatus(FileStatus.DELETED);
            else if (file.getStatus() != FileStatus.REJECTED) return;
            file.setDeletionRequestedAt(null);
            file.setDeletionNotBefore(null);
            file.setDeletionClaimToken(null);
            file.setDeletionLeaseUntil(null);
            file.setDeletionAttemptCount(file.getDeletionAttemptCount() + 1);
            file.setLastDeletionAttemptAt(LocalDateTime.now());
        });
    }

    @Transactional
    public void deletionFailed(UUID fileId, UUID claimToken) {
        repository.findByIdForUpdate(fileId).ifPresent(file -> {
            if (!claimToken.equals(file.getDeletionClaimToken()) || file.getLinkedAt() != null) return;
            if (file.getStatus() != FileStatus.DELETE_PENDING && file.getStatus() != FileStatus.REJECTED) return;
            file.setDeletionAttemptCount(file.getDeletionAttemptCount() + 1);
            LocalDateTime now = LocalDateTime.now();
            file.setLastDeletionAttemptAt(now);
            file.setDeletionNotBefore(now.plus(retryDelay(file.getDeletionAttemptCount())));
            file.setDeletionClaimToken(null);
            file.setDeletionLeaseUntil(null);
        });
    }

    @Transactional
    public void deletionSucceeded(UUID fileId) {
        repository.findByIdForUpdate(fileId).ifPresent(file -> {
            if (file.getLinkedAt() != null) return;
            if (file.getStatus() == FileStatus.DELETE_PENDING) {
                file.setStatus(FileStatus.DELETED);
                file.setDeletionNotBefore(null);
            } else if (file.getStatus() == FileStatus.REJECTED) {
                file.setDeletionRequestedAt(null);
                file.setDeletionNotBefore(null);
            } else {
                return;
            }
            file.setDeletionClaimToken(null);
            file.setDeletionLeaseUntil(null);
            file.setDeletionAttemptCount(file.getDeletionAttemptCount() + 1);
            file.setLastDeletionAttemptAt(LocalDateTime.now());
        });
    }

    @Transactional
    public void deletionFailed(UUID fileId) {
        repository.findByIdForUpdate(fileId).ifPresent(file -> {
            if (file.getStatus() != FileStatus.DELETE_PENDING
                    && !(file.getStatus() == FileStatus.REJECTED
                    && file.getDeletionRequestedAt() != null)) return;
            file.setDeletionAttemptCount(file.getDeletionAttemptCount() + 1);
            file.setLastDeletionAttemptAt(LocalDateTime.now());
            file.setDeletionNotBefore(LocalDateTime.now().plus(retryDelay(file.getDeletionAttemptCount())));
        });
    }

    private Duration retryDelay(int attempt) {
        long delay = retryInitialDelay.toSeconds();
        long max = retryMaxDelay.toSeconds();
        for (int i = 1; i < attempt && delay < max; i++) {
            delay = delay > max / 2 ? max : delay * 2;
        }
        return Duration.ofSeconds(Math.min(delay, max));
    }
}
