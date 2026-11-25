package com.moldy.moldymarket.common.storage.repository;

import com.moldy.moldymarket.common.storage.entity.StoredFile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    Optional<StoredFile> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from StoredFile f where f.id in :fileIds order by f.id")
    List<StoredFile> findAllByIdForUpdate(@Param("fileIds") List<UUID> fileIds);

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

    @Query(value = """
            SELECT * FROM stored_files
            WHERE status = 'PENDING' AND created_at < :cutoff AND linked_at IS NULL
            ORDER BY created_at, id
            LIMIT :batchSize
            """, nativeQuery = true)
    List<StoredFile> findExpiredPendingFiles(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("batchSize") int batchSize);

    @Query(value = """
            SELECT * FROM stored_files
            WHERE (status = 'DELETE_PENDING'
                    OR (status = 'REJECTED' AND deletion_requested_at IS NOT NULL))
              AND linked_at IS NULL
              AND (deletion_not_before IS NULL OR deletion_not_before <= CURRENT_TIMESTAMP)
            ORDER BY deletion_requested_at, id
            LIMIT :batchSize
            """, nativeQuery = true)
    List<StoredFile> findPendingDeletions(@Param("batchSize") int batchSize);

    @Query(value = """
            WITH candidates AS (
                SELECT id FROM stored_files
                WHERE linked_at IS NULL
                  AND (deletion_lease_until IS NULL OR deletion_lease_until <= CURRENT_TIMESTAMP)
                  AND (deletion_not_before IS NULL OR deletion_not_before <= CURRENT_TIMESTAMP)
                  AND ((status = 'PENDING' AND created_at < :cutoff)
                    OR status = 'DELETE_PENDING'
                    OR (status = 'REJECTED' AND deletion_requested_at IS NOT NULL))
                ORDER BY COALESCE(deletion_not_before, created_at), id
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            ), claimed AS (
                UPDATE stored_files f
                SET status = CASE WHEN f.status = 'PENDING' THEN 'DELETE_PENDING' ELSE f.status END,
                    deletion_requested_at = COALESCE(f.deletion_requested_at, CURRENT_TIMESTAMP),
                    deletion_not_before = CURRENT_TIMESTAMP,
                    deletion_claim_token = :claimToken,
                    deletion_lease_until = :leaseUntil
                FROM candidates c
                WHERE f.id = c.id
                RETURNING f.id
            )
            SELECT id FROM claimed
            """, nativeQuery = true)
    List<UUID> claimDeletionBatch(@Param("cutoff") LocalDateTime cutoff,
            @Param("batchSize") int batchSize, @Param("claimToken") UUID claimToken,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    List<StoredFile> findAllByDeletionClaimToken(UUID claimToken);
}
