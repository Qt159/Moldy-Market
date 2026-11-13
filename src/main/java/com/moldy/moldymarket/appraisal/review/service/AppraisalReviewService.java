package com.moldy.moldymarket.appraisal.review.service;

import com.moldy.moldymarket.appraisal.assignment.entity.AppraisalAssignment;
import com.moldy.moldymarket.appraisal.assignment.enums.AppraisalAssignmentStatus;
import com.moldy.moldymarket.appraisal.assignment.repository.AppraisalAssignmentRepository;
import com.moldy.moldymarket.appraisal.report.enums.AppraisalReportStatus;
import com.moldy.moldymarket.appraisal.report.repository.AppraisalReportRepository;
import com.moldy.moldymarket.appraisal.review.dto.request.CreateAppraisalReviewDto;
import com.moldy.moldymarket.appraisal.review.dto.response.AppraisalReviewResponse;
import com.moldy.moldymarket.appraisal.review.entity.AppraisalReview;
import com.moldy.moldymarket.appraisal.review.mapper.AppraisalReviewMapper;
import com.moldy.moldymarket.appraisal.review.repository.AppraisalReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppraisalReviewService {

    private final AppraisalReviewRepository reviewRepository;
    private final AppraisalAssignmentRepository assignmentRepository;
    private final AppraisalReportRepository reportRepository;
    private final AppraisalReviewMapper reviewMapper;


    @Transactional
    public AppraisalReviewResponse create(
            UUID assignmentId,
            UUID sellerId,
            CreateAppraisalReviewDto dto) {
        AppraisalAssignment assignment = assignmentRepository
                .findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Appraisal assignment not found"));

        validateSeller(assignment, sellerId);
        validateAssignmentCompleted(assignment);
        validateAcceptedReport(assignmentId);
        validateReviewNotExists(assignmentId);

        AppraisalReview review = reviewMapper.toEntity(
                dto,
                assignment.getId(),
                sellerId,
                assignment.getAppraiserId());

        return reviewMapper.toResponse(
                reviewRepository.save(review));
    }

    
    public AppraisalReviewResponse getByAssignment(
            UUID assignmentId, UUID userId) {
        AppraisalAssignment assignment = findAssignment(assignmentId);

        validateParticipant(assignment, userId);

        AppraisalReview review = reviewRepository
                .findByAssignmentId(assignmentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Appraisal review not found"));

        return reviewMapper.toResponse(review);
    }

    public Page<AppraisalReviewResponse> getAppraiserReviews(
            UUID appraiserId, Pageable pageable) {
        return reviewRepository
                .findAllByAppraiserId(appraiserId, pageable)
                .map(reviewMapper::toResponse);
    }


    public Page<AppraisalReviewResponse> getMyReviews(
            UUID sellerId, Pageable pageable) {
        return reviewRepository
                .findAllBySellerId(sellerId, pageable)
                .map(reviewMapper::toResponse);
    }

    private AppraisalAssignment findAssignment(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Appraisal assignment not found"));
    }

    private void validateSeller(
            AppraisalAssignment assignment,
            UUID sellerId) {
        if (!assignment.getSellerId().equals(sellerId)) {
            throw new IllegalStateException(
                    "You are not the owner of this assignment");
        }
    }

    private void validateParticipant(
            AppraisalAssignment assignment, UUID userId) {
        boolean isSeller = assignment.getSellerId().equals(userId);
        boolean isAppraiser = assignment.getAppraiserId().equals(userId);

        if (!isSeller && !isAppraiser) {
            throw new IllegalStateException(
                    "You are not allowed to access this review");
        }
    }

    private void validateAssignmentCompleted(AppraisalAssignment assignment) {
        if (assignment.getStatus() != AppraisalAssignmentStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Only completed assignments can be reviewed");
        }
    }

    private void validateAcceptedReport(UUID assignmentId) {
        boolean accepted = reportRepository.existsByAssignmentIdAndStatus(
                assignmentId, AppraisalReportStatus.ACCEPTED);

        if (!accepted) {
            throw new IllegalStateException(
                    "An accepted appraisal report is required before reviewing");
        }
    }

    private void validateReviewNotExists(UUID assignmentId) {
        if (reviewRepository.existsByAssignmentId(assignmentId)) {
            throw new IllegalStateException(
                    "This assignment has already been reviewed");
        }
    }
}