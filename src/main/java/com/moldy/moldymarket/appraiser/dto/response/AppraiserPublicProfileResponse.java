package com.moldy.moldymarket.appraiser.dto.response;

import com.moldy.moldymarket.appraiser.enums.AppraiserStatus;
import com.moldy.moldymarket.appraiser.enums.VerificationLevel;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
// cho seller check
public class AppraiserPublicProfileResponse {

    private UUID id;
    private String fullName;
    private String avatarUrl;
    private String bio;

    private AppraiserStatus status;
    private VerificationLevel verificationLevel;

    private int totalCompletedAppraisals;
    private BigDecimal averageRating;
    private int reviewCount;

    private List<AppraiserCategoryResponse> categories;
}