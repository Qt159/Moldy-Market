package com.moldy.moldymarket.commission.mapper;

import com.moldy.moldymarket.commission.dto.response.AppraiserCommissionResponse;
import com.moldy.moldymarket.commission.entity.AppraiserCommission;
import org.springframework.stereotype.Component;

/**
 * Mapper for AppraiserCommission entity to DTO conversion
 */
@Component
public class AppraiserCommissionMapper {

    public AppraiserCommissionResponse toResponse(AppraiserCommission commission) {
        if (commission == null) {
            return null;
        }

        return AppraiserCommissionResponse.builder()
            .id(commission.getId())
            .workflowId(commission.getWorkflowId())
            .appraiserId(commission.getAppraiserId())
            .agreedFee(commission.getAgreedFee())
            .platformFee(commission.getPlatformFee())
            .netEarnings(commission.getNetEarnings())
            .status(commission.getStatus())
            .earnedAt(commission.getEarnedAt())
            .paidAt(commission.getPaidAt())
            .createdAt(commission.getCreatedAt())
            .updatedAt(commission.getUpdatedAt())
            .build();
    }
}
