package com.moldy.moldymarket.commission.service;

import com.moldy.moldymarket.appraisal.assignment.entity.AppraisalAssignment;
import com.moldy.moldymarket.appraisal.assignment.enums.AppraisalAssignmentStatus;
import com.moldy.moldymarket.appraisal.assignment.repository.AppraisalAssignmentRepository;
import com.moldy.moldymarket.appraiser.entity.Appraiser;
import com.moldy.moldymarket.appraiser.repository.AppraiserRepository;
import com.moldy.moldymarket.commission.dto.response.AppraiserCommissionResponse;
import com.moldy.moldymarket.commission.entity.AppraiserCommission;
import com.moldy.moldymarket.commission.mapper.AppraiserCommissionMapper;
import com.moldy.moldymarket.commission.repository.AppraiserCommissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AppraiserCommissionService {

    private final AppraiserCommissionRepository commissionRepository;
    private final AppraisalAssignmentRepository assignmentRepository;
    private final AppraiserRepository appraiserRepository;
    private final AppraiserCommissionMapper commissionMapper;

    //appraisal assignment đã completed mới tạo
    public AppraiserCommissionResponse createCommission(UUID assignmentId) {

        var existingCommission =
            commissionRepository.findByAssignmentId(assignmentId);

        if (existingCommission.isPresent()) {
            return commissionMapper.toResponse(existingCommission.get());
        }

        AppraisalAssignment assignment = assignmentRepository
            .findById(assignmentId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Appraisal assignment not found"));

        if (assignment.getStatus() != AppraisalAssignmentStatus.COMPLETED) {
            throw new IllegalStateException(
                "Commission can only be created for completed assignments");
        }

        Appraiser appraiser = appraiserRepository
            .findById(assignment.getAppraiserId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Appraiser not found"));

        AppraiserCommission commission = new AppraiserCommission(
            assignment.getId(),
            appraiser.getId(),
            appraiser.getVerificationLevel(),
            assignment.getAgreedFee());

        AppraiserCommission savedCommission =
            commissionRepository.save(commission);

        return commissionMapper.toResponse(savedCommission);
    }

    
    @Transactional(readOnly = true)
    public List<AppraiserCommissionResponse> getMyCommissions(UUID appraiserId) {
        return commissionRepository
            .findAllByAppraiserIdOrderByCalculatedAtDesc(appraiserId)
            .stream()
            .map(commissionMapper::toResponse)
            .toList();
    }

 
    @Transactional(readOnly = true)
    public AppraiserCommissionResponse getMyCommission(UUID commissionId, UUID appraiserId) {
        AppraiserCommission commission = commissionRepository
            .findByIdAndAppraiserId(commissionId, appraiserId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Commission not found"));

        return commissionMapper.toResponse(commission);
    }

    public AppraiserCommissionResponse markAsPaid(UUID commissionId) {
        AppraiserCommission commission = getCommissionOrThrow(commissionId);
        commission.markAsPaid();

        return commissionMapper.toResponse(commission);
    }

   
    public void cancelCommission(UUID commissionId) {
        AppraiserCommission commission = getCommissionOrThrow(commissionId);

        commission.cancel();
    }

    private AppraiserCommission getCommissionOrThrow(UUID commissionId) {
        return commissionRepository.findById(commissionId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Commission not found"));
    }
}
