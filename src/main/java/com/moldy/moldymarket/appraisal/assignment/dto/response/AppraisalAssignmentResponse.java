package com.moldy.moldymarket.appraisal.assignment.dto.response;

import com.moldy.moldymarket.appraisal.assignment.enums.AppraisalAssignmentStatus;
import com.moldy.moldymarket.appraisal.assignment.enums.AppraisalSourceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AppraisalAssignmentResponse(

    UUID id,
    UUID appraisalRequestId,
    UUID sellerId,
    UUID appraiserId,
    UUID applicationId,
    UUID invitationId,
    AppraisalSourceType sourceType,
    BigDecimal agreedFee,
    UUID conversationId,
    AppraisalAssignmentStatus status,
    Instant assignedAt,
    Instant startedAt,
    Instant completedAt,
    Instant createdAt,
    Instant updatedAt) {}