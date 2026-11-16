package com.moldy.moldymarket.appraiser.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuspendAppraiserRequest {

    @NotBlank(message = "Suspension reason cannot be blank")
    @Size(max = 2000, message = "Suspension reason cannot exceed 2000 characters")
    private String reason;

    @Future(message = "Suspension until must be in the future")
    private Instant suspensionUntil; // null thì sẽ là vô thời hạn
}