package com.moldy.moldymarket.appraisal.report.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record SubmitAppraisalReportDto(

        @NotBlank
        @Size(min = 20, max = 5000)
        String description,

        @NotBlank
        @Size(min = 20, max = 5000)
        String conditionAssessment,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal assessedValue,

        @Size(max = 10)
        List<String> imageUrls,

        @Size(max = 10)
        List<String> documentUrls

) {
}