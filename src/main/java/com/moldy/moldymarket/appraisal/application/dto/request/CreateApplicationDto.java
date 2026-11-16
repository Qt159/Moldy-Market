package com.moldy.moldymarket.appraisal.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateApplicationDto(

    @NotBlank(message = "Introduction is required")
    @Size(min = 20, max = 1000, message = "Introduction must be between 20 and 1000 characters")
    String introduction,

    @NotBlank(message = "Experience description is required")
    @Size(min = 50, max = 5000, message = "Experience description must be between 50 and 5000 characters")
    String experienceDescription,

    @NotNull(message = "Proposed fee is required")
    @DecimalMin(value = "0.01", message = "Proposed fee must be greater than 0")
    BigDecimal proposedFee,

    @NotNull(message = "Estimated completion hours is required")
    @Min(value = 1, message = "Estimated completion hours must be at least 1")
    @Max(value = 480, message = "Estimated completion hours cannot exceed 480")
    Integer estimatedCompletionHours

) {}
