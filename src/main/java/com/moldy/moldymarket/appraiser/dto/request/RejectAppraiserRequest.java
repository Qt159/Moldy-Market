package com.moldy.moldymarket.appraiser.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectAppraiserRequest {

    @NotBlank(message = "Rejection reason cannot be blank")
    @Size(max = 2000, message = "Rejection reason cannot exceed 2000 characters")
    private String rejectionReason;
}