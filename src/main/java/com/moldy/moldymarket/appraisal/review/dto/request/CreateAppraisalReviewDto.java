package com.moldy.moldymarket.appraisal.review.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAppraisalReviewDto(

    @NotNull
    @Min(1)
    @Max(5)
    Integer rating,

    @NotNull
    @Min(1)
    @Max(5)
    Integer accuracyScore,

    @NotNull
    @Min(1)
    @Max(5)
    Integer professionalismScore,

    @NotNull
    @Min(1)
    @Max(5)
    Integer communicationScore,

    @NotNull
    @Min(1)
    @Max(5)
    Integer timelinessScore,

    @Size(max = 2000)
    String comment,

    @NotNull
    Boolean recommend

) {
}