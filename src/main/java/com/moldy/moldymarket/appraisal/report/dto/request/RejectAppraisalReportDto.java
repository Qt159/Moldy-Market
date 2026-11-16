package com.moldy.moldymarket.appraisal.report.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectAppraisalReportDto(

        @NotBlank
        @Size(min = 10, max = 2000)
        String rejectionReason

) {
}