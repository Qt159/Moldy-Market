package com.moldy.moldymarket.pricing.appraiser.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moldy.moldymarket.pricing.appraiser.entity.AppraiserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppraiserProfileResponse(
    UUID id,
    UUID userId,
    String fullName,
    String email,
    AppraiserStatus status,
    Integer totalCompleted,
    Integer accurateCount,
    Double accuracyPercentage,
    String reputationLabel,
    List<UUID> categoryIds,
    Instant createdAt,
    Instant updatedAt
) {}
