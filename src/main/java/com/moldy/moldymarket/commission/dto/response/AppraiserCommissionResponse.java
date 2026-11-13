package com.moldy.moldymarket.commission.dto.response;

import com.moldy.moldymarket.commission.enums.CommissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for appraiser commission
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppraiserCommissionResponse {
    UUID id;
    UUID workflowId;
    UUID appraiserId;
    
    BigDecimal agreedFee;
    BigDecimal platformFee;
    BigDecimal netEarnings;
    
    CommissionStatus status;
    
    Instant earnedAt;
    Instant paidAt;
    
    Instant createdAt;
    Instant updatedAt;
}
