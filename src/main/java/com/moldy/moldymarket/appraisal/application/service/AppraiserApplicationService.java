package com.moldy.moldymarket.appraisal.application.service;

import com.moldy.moldymarket.appraisal.application.dto.request.CreateApplicationDto;
import com.moldy.moldymarket.appraisal.application.dto.response.AppraiserApplicationResponse;
import com.moldy.moldymarket.appraisal.application.entity.AppraiserApplication;
import com.moldy.moldymarket.appraisal.application.enums.AppraiserApplicationStatus;
import com.moldy.moldymarket.appraisal.application.mapper.AppraiserApplicationMapper;
import com.moldy.moldymarket.appraisal.application.repository.AppraiserApplicationRepository;
import com.moldy.moldymarket.appraisal.request.entity.AppraisalRequest;
import com.moldy.moldymarket.appraisal.request.repository.AppraisalRequestRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppraiserApplicationService {

    private final AppraiserApplicationRepository applicationRepository;
    private final AppraisalRequestRepository requestRepository;
    private final AppraiserApplicationMapper applicationMapper;

    
    @Transactional
    public AppraiserApplicationResponse apply(UUID requestId, UUID appraiserId, CreateApplicationDto dto) {
        AppraisalRequest request = findRequestById(requestId);

        if (!request.isOpen()) {
            throw new IllegalStateException("This appraisal request is no longer open");
        }

        if (request.getDeadline() != null
                && !request.getDeadline().isAfter(Instant.now())) {
            throw new IllegalStateException("This appraisal request has expired");
        }

        
        if (request.isSeller(appraiserId)) {
            throw new IllegalStateException("You cannot apply to your own appraisal request");
        }

        // tránh duplicate applications
        if (applicationRepository.existsByAppraisalRequestIdAndAppraiserId(requestId, appraiserId)) {
            throw new IllegalStateException("You have already applied for this request");
        }

        AppraiserApplication application = applicationMapper.toEntity(dto, requestId, appraiserId);

        return applicationMapper.toResponse(applicationRepository.save(application));
    }

   
    public AppraiserApplicationResponse getById(UUID applicationId, UUID userId) {
        AppraiserApplication application = findApplicationById(applicationId);

        boolean isApplicant = application.isOwnedBy(userId);

        AppraisalRequest request = findRequestById(application.getAppraisalRequestId());

        boolean isSeller = request.isSeller(userId);

        if (!isApplicant && !isSeller) {
            throw new AccessDeniedException(
                    "You are not allowed to view this application");
        }

        return applicationMapper.toResponse(application);
    }

   
    public Page<AppraiserApplicationResponse> getByRequest(UUID requestId, UUID sellerId, Pageable pageable) {
        AppraisalRequest request = findRequestById(requestId);

        if (!request.isSeller(sellerId)) {
            throw new AccessDeniedException(
                    "You are not allowed to view applications for this request"
            );
        }

        return applicationRepository
                .findAllByAppraisalRequestId(requestId, pageable)
                .map(applicationMapper::toResponse);
    }

    
    public Page<AppraiserApplicationResponse> getMyApplications(UUID appraiserId, Pageable pageable) {
        return applicationRepository
                .findAllByAppraiserId(appraiserId, pageable)
                .map(applicationMapper::toResponse);
    }

    ///rút lui
    @Transactional
    public void withdraw(UUID applicationId, UUID appraiserId) {
        AppraiserApplication application = applicationRepository
                .findByIdAndAppraiserId(applicationId, appraiserId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Application not found: " + applicationId));

        if (application.getStatus() != AppraiserApplicationStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending applications can be withdrawn");
        }

        application.withdraw();
    }

    private AppraisalRequest findRequestById(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Appraisal request not found: " + requestId ));
    }

    private AppraiserApplication findApplicationById(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Application not found: " + applicationId));
    }
}
