package com.moldy.moldymarket.appraisal.request.service;

import com.moldy.moldymarket.appraisal.enums.AppraisalRequestStatus;
import com.moldy.moldymarket.appraisal.request.dto.request.CreateAppraisalRequestDto;
import com.moldy.moldymarket.appraisal.request.dto.response.AppraisalRequestResponse;
import com.moldy.moldymarket.appraisal.request.entity.AppraisalRequest;
import com.moldy.moldymarket.appraisal.request.mapper.AppraisalRequestMapper;
import com.moldy.moldymarket.appraisal.request.repository.AppraisalRequestRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppraisalRequestService {

    private final AppraisalRequestRepository requestRepository;
    private final AppraisalRequestMapper requestMapper;

    @Transactional
    public AppraisalRequestResponse create(CreateAppraisalRequestDto dto, UUID sellerId) {
        AppraisalRequest request = requestMapper.toEntity(dto, sellerId);

        return requestMapper.toResponse(requestRepository.save(request));
    }

    public AppraisalRequestResponse getById(UUID requestId) {
        return requestMapper.toResponse(findRequestById(requestId));
    }

    public Page<AppraisalRequestResponse> getMyRequests(UUID sellerId, Pageable pageable) {
        return requestRepository.findAllBySellerId(sellerId, pageable)
                            .map(requestMapper::toResponse);
    }

    public Page<AppraisalRequestResponse> getOpenRequests(Pageable pageable) {
        return requestRepository
                .findAllByStatus(AppraisalRequestStatus.OPEN, pageable)
                .map(requestMapper::toResponse);
    }

    @Transactional
    public void cancel(UUID requestId, UUID sellerId) {
        AppraisalRequest request = findRequestById(requestId);

        if (!request.isSeller(sellerId)) {
            throw new AccessDeniedException(
                    "You are not allowed to cancel this request");
        }

        request.cancel();
    }

    private AppraisalRequest findRequestById(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Appraisal request not found: " + requestId));
    }
}
