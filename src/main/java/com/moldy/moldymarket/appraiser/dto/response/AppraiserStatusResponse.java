package com.moldy.moldymarket.appraiser.dto.response;

import com.moldy.moldymarket.appraiser.enums.AppraiserStatus;
import com.moldy.moldymarket.appraiser.enums.VerificationLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class AppraiserStatusResponse {

    private UUID id;
    private UUID userId;

    private AppraiserStatus status;
    private VerificationLevel verificationLevel;

    private String rejectionReason;

    private String suspensionReason;
    private Instant suspensionUntil;

    private Instant updatedAt;
}