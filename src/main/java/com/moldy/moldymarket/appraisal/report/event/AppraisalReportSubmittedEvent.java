package com.moldy.moldymarket.appraisal.report.event;

import java.util.UUID;

public record AppraisalReportSubmittedEvent(
        UUID reportId,
        UUID assignmentId,
        UUID sellerId,
        UUID appraiserId) {
}
