package com.moldy.moldymarket.appraiser.dto.response;

import com.moldy.moldymarket.appraiser.enums.AppraiserStatus;
import com.moldy.moldymarket.appraiser.enums.VerificationLevel;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
// cho admin quản trị duyệt
public class AppraiserProfileResponse {

    private UUID id;
    private UUID userId;

    private String fullName;
    private String email;
    private String avatarUrl;
    private String bio;

    private AppraiserStatus status;
    private VerificationLevel verificationLevel;

    private int totalCompletedAppraisals;
    private BigDecimal averageRating;
    private int reviewCount;

    private List<AppraiserCategoryResponse> categories;

    private Instant createdAt;
    private Instant updatedAt;
}