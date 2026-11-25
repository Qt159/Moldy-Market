package com.moldy.moldymarket.appraisal.report.service;

import com.moldy.moldymarket.appraisal.assignment.entity.AppraisalAssignment;
import com.moldy.moldymarket.appraisal.assignment.enums.AppraisalAssignmentStatus;
import com.moldy.moldymarket.appraisal.assignment.repository.AppraisalAssignmentRepository;
import com.moldy.moldymarket.appraisal.report.dto.request.SubmitAppraisalReportDto;
import com.moldy.moldymarket.appraisal.report.dto.response.AppraisalReportResponse;
import com.moldy.moldymarket.appraisal.report.entity.AppraisalReport;
import com.moldy.moldymarket.appraisal.report.event.AppraisalReportSubmittedEvent;
import com.moldy.moldymarket.appraisal.report.enums.AppraisalReportStatus;
import com.moldy.moldymarket.appraisal.report.mapper.AppraisalReportMapper;
import com.moldy.moldymarket.appraisal.report.repository.AppraisalReportRepository;
import com.moldy.moldymarket.appraisal.request.entity.AppraisalRequest;
import com.moldy.moldymarket.appraisal.request.repository.AppraisalRequestRepository;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.storage.enums.FilePurpose;
import com.moldy.moldymarket.common.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppraisalReportService {

    private static final int MAX_REJECTIONS = 3;

    private final AppraisalReportRepository reportRepository;
    private final AppraisalAssignmentRepository assignmentRepository;
    private final AppraisalRequestRepository requestRepository;
    private final AppraisalReportMapper reportMapper;
    private final ApplicationEventPublisher events;
    private final FileStorageService fileStorageService;

    
    @Transactional
    public AppraisalReportResponse submit(
            UUID assignmentId,
            UUID appraiserId,
            SubmitAppraisalReportDto dto) {
        AppraisalAssignment assignment =
                findAssignmentForUpdate(assignmentId);

        validateAppraiser(assignment, appraiserId);
        validateAssignmentInProgress(assignment);
        validateRejectionLimit(assignment);

        if (dto.mediaFileIds() != null && !dto.mediaFileIds().isEmpty()
                && ((dto.imageUrls() != null && !dto.imageUrls().isEmpty())
                || (dto.documentUrls() != null && !dto.documentUrls().isEmpty()))) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Use mediaFileIds instead of legacy media URLs");
        }
        List<UUID> mediaIds = dto.mediaFileIds() == null ? List.of() : dto.mediaFileIds();
        fileStorageService.validateAndLinkFiles(mediaIds, appraiserId, FilePurpose.APPRAISAL_EVIDENCE);

        if (reportRepository.existsByAssignmentIdAndStatus(
                assignmentId,
                AppraisalReportStatus.SUBMITTED)) {
            throw new IllegalStateException(
                    "A submitted report is awaiting seller review");
        }

        int revisionNumber = reportRepository
                .findFirstByAssignmentIdOrderByRevisionNumberDesc(assignmentId)
                .map(report -> report.getRevisionNumber() + 1)
                .orElse(1);

        AppraisalReport report = reportMapper.toEntity(
                dto,
                assignmentId,
                appraiserId,
                revisionNumber
        );

        AppraisalReport saved = reportRepository.save(report);
        saved.setMediaFileIds(mediaIds);
        events.publishEvent(new AppraisalReportSubmittedEvent(
                saved.getId(), assignment.getId(), assignment.getSellerId(),
                assignment.getAppraiserId()));
        return withMedia(reportMapper.toResponse(saved), saved);
    }

    // Seller đồng ý report.
    @Transactional
    public AppraisalReportResponse accept(UUID reportId, UUID sellerId) {
        AppraisalReport report = findReportById(reportId);

        AppraisalAssignment assignment = findAssignmentForUpdate(report.getAssignmentId());

        validateSeller(assignment, sellerId);
        validateAssignmentInProgress(assignment);
        validateSubmittedReport(report);

        report.accept();
        assignment.complete();

        AppraisalRequest request = requestRepository
                .findById(assignment.getAppraisalRequestId())
                .orElseThrow(() -> new IllegalStateException(
                        "Appraisal request not found"));

        request.complete();

        return withMedia(reportMapper.toResponse(report), report);
    }

    //Seller reject
    @Transactional
    public AppraisalReportResponse reject(UUID reportId, UUID sellerId, String rejectionReason) {
        AppraisalReport report = findReportById(reportId);

        AppraisalAssignment assignment =findAssignmentForUpdate(report.getAssignmentId());

        validateSeller(assignment, sellerId);
        validateAssignmentInProgress(assignment);
        validateSubmittedReport(report);
        validateRejectionLimit(assignment);

        report.reject(rejectionReason);
        assignment.incrementRejectionCount();

        return withMedia(reportMapper.toResponse(report), report);
    }


    public List<AppraisalReportResponse> getHistory(UUID assignmentId, UUID userId) {
        AppraisalAssignment assignment = findAssignmentById(assignmentId);

        validateParticipant(assignment, userId);

        return reportRepository
                .findAllByAssignmentIdOrderByRevisionNumberDesc(assignmentId)
                .stream()
                .map(report -> withMedia(reportMapper.toResponse(report), report))
                .toList();
    }

    
    public AppraisalReportResponse getById(UUID reportId, UUID userId) {
        AppraisalReport report = findReportById(reportId);
        AppraisalAssignment assignment =
                findAssignmentById(report.getAssignmentId());

        validateParticipant(assignment, userId);

        return withMedia(reportMapper.toResponse(report), report);
    }

    private AppraisalAssignment findAssignmentForUpdate(UUID assignmentId) {
        return assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Appraisal assignment not found"));
    }

    private AppraisalAssignment findAssignmentById(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Appraisal assignment not found"));
    }

    private AppraisalReport findReportById(UUID reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalStateException(
                        "Appraisal report not found"));
    }

    private void validateAppraiser(
            AppraisalAssignment assignment, UUID appraiserId) {
        if (!assignment.getAppraiserId().equals(appraiserId)) {
            throw new IllegalStateException(
                    "You are not the assigned appraiser");
        }
    }

    private void validateSeller(AppraisalAssignment assignment, UUID sellerId) {
        if (!assignment.getSellerId().equals(sellerId)) {
            throw new IllegalStateException(
                    "You are not the owner of this assignment");
        }
    }

    private void validateParticipant(AppraisalAssignment assignment, UUID userId) {
        boolean isSeller = assignment.getSellerId().equals(userId);
        boolean isAppraiser = assignment.getAppraiserId().equals(userId);

        if (!isSeller && !isAppraiser) {
            throw new IllegalStateException(
                    "You are not allowed to access this report");
        }
    }

    private void validateAssignmentInProgress(AppraisalAssignment assignment) {
        if (assignment.getStatus() != AppraisalAssignmentStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Assignment must be in progress");
        }
    }

    private void validateSubmittedReport(AppraisalReport report) {
        if (report.getStatus() != AppraisalReportStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Only submitted reports can be reviewed");
        }
    }

    private void validateRejectionLimit(AppraisalAssignment assignment) {
        if (assignment.getRejectionCount() >= MAX_REJECTIONS) {
            throw new IllegalStateException(
                    "The maximum number of report rejections has been reached");
        }
    }

    private AppraisalReportResponse withMedia(AppraisalReportResponse response, AppraisalReport report) {
        var media = fileStorageService.getMediaViews(report.getMediaFileIds());
        return new AppraisalReportResponse(response.id(), response.assignmentId(), response.appraiserId(),
                response.revisionNumber(), response.description(), response.conditionAssessment(),
                response.assessedValue(), response.imageUrls(), response.documentUrls(), response.status(),
                response.rejectionReason(), response.submittedAt(), response.reviewedAt(), report.getMediaFileIds(),
                media == null ? List.of() : media);
    }
}
