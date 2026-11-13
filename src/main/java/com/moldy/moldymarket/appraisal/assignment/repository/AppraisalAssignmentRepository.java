package com.moldy.moldymarket.appraisal.assignment.repository;

import com.moldy.moldymarket.appraisal.assignment.entity.AppraisalAssignment;
import com.moldy.moldymarket.appraisal.assignment.enums.AppraisalAssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AppraisalAssignmentRepository
        extends JpaRepository<AppraisalAssignment, UUID> {

    Optional<AppraisalAssignment> findByAppraisalRequestId(UUID appraisalRequestId);

    boolean existsByAppraisalRequestId(UUID appraisalRequestId);

    Page<AppraisalAssignment> findAllByAppraiserId(UUID appraiserId, Pageable pageable);

    Page<AppraisalAssignment> findAllBySellerId(UUID sellerId, Pageable pageable);

    Page<AppraisalAssignment> findAllByAppraiserIdAndStatus(
            UUID appraiserId, AppraisalAssignmentStatus status, Pageable pageable);

    Page<AppraisalAssignment> findAllBySellerIdAndStatus(
            UUID sellerId,
            AppraisalAssignmentStatus status,
            Pageable pageable);

    Optional<AppraisalAssignment> findByIdAndAppraiserId(UUID id, UUID appraiserId);

    Optional<AppraisalAssignment> findByIdAndSellerId(UUID id, UUID sellerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AppraisalRequest r WHERE r.id = :requestId")
    Optional<AppraisalRequest> findByIdForUpdate(
        @Param("requestId") UUID requestId);
}