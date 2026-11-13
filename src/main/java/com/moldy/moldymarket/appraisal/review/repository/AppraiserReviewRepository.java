package com.moldy.moldymarket.appraisal.review.repository;

import com.moldy.moldymarket.appraisal.review.entity.AppraisalReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppraisalReviewRepository
        extends JpaRepository<AppraisalReview, UUID> {

    boolean existsByAssignmentId(UUID assignmentId);

    Optional<AppraisalReview> findByAssignmentId(UUID assignmentId);

    Page<AppraisalReview> findAllByAppraiserId(
            UUID appraiserId,
            Pageable pageable);

    Page<AppraisalReview> findAllBySellerId(
            UUID sellerId,
            Pageable pageable);
}