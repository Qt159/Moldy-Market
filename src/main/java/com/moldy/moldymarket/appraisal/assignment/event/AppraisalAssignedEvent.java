package com.moldy.moldymarket.appraisal.assignment.event;

import java.util.UUID;

public record AppraisalAssignedEvent(
        UUID assignmentId,
        UUID appraisalRequestId,
        UUID sellerId,
        UUID appraiserId,
        UUID actorId) {
}
