package com.moldy.moldymarket.common.storage.repository;

import com.moldy.moldymarket.common.storage.entity.StoredFile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    Optional<StoredFile> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT f
            FROM StoredFile f
            WHERE f.id = :fileId
            """)
    Optional<StoredFile> findByIdForUpdate(
            @Param("fileId") UUID fileId
    );

    boolean existsByObjectKey(String objectKey);
}