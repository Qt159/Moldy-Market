package com.moldy.moldymarket.appraisal.request.repository;

import com.moldy.moldymarket.appraisal.request.entity.AppraisalRequestStatus;
import com.moldy.moldymarket.appraisal.request.entity.AppraisalRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AppraisalRequestRepository
        extends JpaRepository<AppraisalRequest, UUID> {

    Page<AppraisalRequest> findAllBySellerId(UUID sellerId, Pageable pageable);
    Page<AppraisalRequest> findAllByStatus(AppraisalRequestStatus status,Pageable pageable);
    boolean existsByIdAndSellerId(UUID id, UUID sellerId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AppraisalRequest r WHERE r.id = :id")
    Optional<AppraisalRequest> findByIdForUpdate(@Param("id") UUID id);
}
