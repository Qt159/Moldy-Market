package com.moldy.moldymarket.common.storage.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.storage.entity.StoredFile;
import com.moldy.moldymarket.common.storage.enums.FilePurpose;
import com.moldy.moldymarket.common.storage.enums.FileStatus;
import com.moldy.moldymarket.common.storage.repository.StoredFileRepository;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Central file access gate. Cross-resource access is intentionally denied until
 * business modules persist StoredFile IDs instead of opaque URL strings.
 */
@Service
@Slf4j
public class FileAuthorizationService {

    private final StoredFileRepository repository;

    public FileAuthorizationService(StoredFileRepository repository) {
        this.repository = repository;
    }

    public StoredFile requireOwner(UUID fileId, UUID requesterId) {
        return repository.findByIdAndOwnerId(fileId, requesterId)
                .orElseThrow(() -> {
                    log.warn("File access denied. fileId={}, requesterId={}", fileId, requesterId);
                    return new AppException(ErrorCode.RESOURCE_NOT_FOUND);
                });
    }

    public void requireOwner(StoredFile file, UUID requesterId) {
        if (!file.getOwnerId().equals(requesterId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    public void requireAvailable(StoredFile file) {
        if (file.getStatus() != FileStatus.AVAILABLE) {
            throw new AppException(ErrorCode.FILE_NOT_AVAILABLE);
        }
    }

    public void requirePurpose(StoredFile file, FilePurpose expectedPurpose) {
        if (file.getPurpose() != expectedPurpose) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "File purpose does not match the resource");
        }
    }
}
