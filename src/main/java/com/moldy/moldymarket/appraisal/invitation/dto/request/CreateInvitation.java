
package com.moldy.moldymarket.appraisal.invitation.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateInvitationDto(

        @NotNull(message = "Appraiser ID is required")
        UUID appraiserId,

        @NotNull(message = "Proposed fee is required")
        @DecimalMin(value = "0.01", message = "Proposed fee must be greater than 0")
        BigDecimal proposedFee,

        @NotBlank(message = "Message is required")
        @Size(min = 10, max = 2000, message = "Message must be between 10 and 2000 characters")
        String message,

        @NotNull(message = "Deadline is required")
        @Future(message = "Deadline must be in the future")
        Instant deadline

) {}
