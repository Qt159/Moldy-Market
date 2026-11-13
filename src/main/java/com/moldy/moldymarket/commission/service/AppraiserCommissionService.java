package com.moldy.moldymarket.commission.service;

import com.moldy.moldymarket.commission.dto.response.AppraiserCommissionResponse;
import com.moldy.moldymarket.commission.entity.AppraiserCommission;
import com.moldy.moldymarket.commission.enums.CommissionStatus;
import com.moldy.moldymarket.commission.mapper.AppraiserCommissionMapper;
import com.moldy.moldymarket.commission.repository.AppraiserCommissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service for managing appraiser commissions
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AppraiserCommissionService {
    private final AppraiserCommissionRepository commissionRepository;
    private final AppraiserCommissionMapper mapper;

    /**
     * Create a new commission for an appraiser
     */
    public AppraiserCommissionResponse createCommission(UUID workflowId, UUID appraiserId, BigDecimal agreedFee) {
        AppraiserCommission commission = new AppraiserCommission(workflowId, appraiserId, agreedFee);
        AppraiserCommission saved = commissionRepository.save(commission);
        return mapper.toResponse(saved);
    }

    /**
     * Mark commission as earned
     */
    public AppraiserCommissionResponse markEarned(UUID commissionId, UUID appraiserId) {
        AppraiserCommission commission = commissionRepository.findById(commissionId)
            .orElseThrow(() -> new RuntimeException("Commission not found"));

        if (!commission.getAppraiserId().equals(appraiserId)) {
            throw new RuntimeException("Not the owner of this commission");
        }

        commission.markEarned(BigDecimal.ZERO);
        AppraiserCommission updated = commissionRepository.save(commission);
        return mapper.toResponse(updated);
    }

    /**
     * Mark commission as paid
     */
    public AppraiserCommissionResponse markPaid(UUID commissionId) {
        AppraiserCommission commission = commissionRepository.findById(commissionId)
            .orElseThrow(() -> new RuntimeException("Commission not found"));

        commission.markPaid();
        AppraiserCommission updated = commissionRepository.save(commission);
        return mapper.toResponse(updated);
    }

    /**
     * Get commission by workflow ID
     */
    public AppraiserCommissionResponse getByWorkflowId(UUID workflowId) {
        AppraiserCommission commission = commissionRepository.findByWorkflowId(workflowId)
            .orElseThrow(() -> new RuntimeException("Commission not found"));
        return mapper.toResponse(commission);
    }

    /**
     * Get appraiser's commissions paginated
     */
    public Page<AppraiserCommissionResponse> getAppraiserCommissions(UUID appraiserId, Pageable pageable) {
        return commissionRepository.findByAppraiserId(appraiserId, pageable)
            .map(mapper::toResponse);
    }

    /**
     * Get appraiser's commissions by status
     */
    public Page<AppraiserCommissionResponse> getAppraiserCommissionsByStatus(UUID appraiserId, CommissionStatus status, Pageable pageable) {
        return commissionRepository.findByAppraiserIdAndStatus(appraiserId, status, pageable)
            .map(mapper::toResponse);
    }

    /**
     * Get total earnings for appraiser
     */
    public BigDecimal getTotalEarnings(UUID appraiserId) {
        return commissionRepository.getTotalEarningsByAppraiser(appraiserId);
    }

    /**
     * Get count of commissions by status
     */
    public int getCountByStatus(UUID appraiserId, CommissionStatus status) {
        return commissionRepository.countByAppraiserIdAndStatus(appraiserId, status);
    }
}
