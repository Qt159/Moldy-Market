package com.moldy.moldymarket.appraisal.report.dto.response;

import com.moldy.moldymarket.appraisal.report.enums.AppraisalReportStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AppraisalReportResponse(
    UUID id,
    UUID assignmentId,
    UUID appraiserId,
    Integer revisionNumber,
    String description,
    String conditionAssessment,
    BigDecimal assessedValue,
    List<String> imageUrls,
    List<String> documentUrls,
    AppraisalReportStatus status,
    String rejectionReason,
    Instant submittedAt,
    Instant reviewedAt

) {
}