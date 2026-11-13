package com.moldy.moldymarket.appraisal.invitation.service;

import com.moldy.moldymarket.appraisal.invitation.dto.request.CreateInvitationDto;
import com.moldy.moldymarket.appraisal.invitation.dto.response.AppraisalInvitationResponse;
import com.moldy.moldymarket.appraisal.invitation.entity.AppraisalInvitation;
import com.moldy.moldymarket.appraisal.invitation.enums.AppraisalInvitationStatus;
import com.moldy.moldymarket.appraisal.invitation.mapper.AppraisalInvitationMapper;
import com.moldy.moldymarket.appraisal.invitation.repository.AppraisalInvitationRepository;
import com.moldy.moldymarket.appraisal.request.entity.AppraisalRequest;
import com.moldy.moldymarket.appraisal.request.repository.AppraisalRequestRepository;
import com.moldy.moldymarket.appraisal.assignment.service.AppraisalAssignmentService;
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
public class AppraisalInvitationService {

    private final AppraisalInvitationRepository invitationRepository;
    private final AppraisalRequestRepository requestRepository;
    private final AppraisalInvitationMapper invitationMapper;
    private final AppraisalAssignmentService assignmentService;


    @Transactional
    public AppraisalInvitationResponse create(
            UUID requestId, UUID sellerId, CreateInvitationDto dto) {
        AppraisalRequest request = findRequestById(requestId);

        if (!request.isSeller(sellerId)) {
            throw new AccessDeniedException(
                    "You are not allowed to invite an appraiser for this request");
        }

        if (!request.isOpen()) {
            throw new IllegalStateException(
                    "Only open appraisal requests can receive invitations");
        }

        if (request.getDeadline() != null
                && !request.getDeadline().isAfter(Instant.now())) {
            throw new IllegalStateException(
                    "This appraisal request has expired");
        }

        if (request.isSeller(dto.appraiserId())) {
            throw new IllegalArgumentException(
                    "You cannot invite yourself as an appraiser");
        }

        if (!dto.deadline().isAfter(Instant.now())) {
            throw new IllegalArgumentException(
                    "Invitation deadline must be in the future");
        }

        if (dto.deadline().isAfter(request.getDeadline())) {
            throw new IllegalArgumentException(
                    "Invitation deadline cannot exceed request deadline");
        }

        if (invitationRepository.existsByAppraisalRequestIdAndAppraiserId(
                requestId, dto.appraiserId())) {
            throw new IllegalStateException(
                    "This appraiser has already been invited to this request");
        }

        AppraisalInvitation invitation = invitationMapper.toEntity(dto, requestId);

        return invitationMapper.toResponse(invitationRepository.save(invitation));
    }

   
    public AppraisalInvitationResponse getById(UUID invitationId, UUID userId) {
        AppraisalInvitation invitation = findInvitationById(invitationId);
        AppraisalRequest request = findRequestById(
                                    invitation.getAppraisalRequestId());

        boolean isInvitedAppraiser = invitation.isOwnedBy(userId);
        boolean isSeller = request.isSeller(userId);

        if (!isInvitedAppraiser && !isSeller) {
            throw new AccessDeniedException(
                    "You are not allowed to view this invitation");
        }

        return invitationMapper.toResponse(invitation);
    }

    
    public Page<AppraisalInvitationResponse> getByRequest(
            UUID requestId, UUID sellerId, Pageable pageable) {
        AppraisalRequest request = findRequestById(requestId);

        if (!request.isSeller(sellerId)) {
            throw new AccessDeniedException(
                    "You are not allowed to view invitations for this request");
        }

        return invitationRepository
                .findAllByAppraisalRequestId(requestId, pageable)
                .map(invitationMapper::toResponse);
    }

    public Page<AppraisalInvitationResponse> getMyInvitations(
            UUID appraiserId, Pageable pageable) {
        return invitationRepository
                .findAllByAppraiserId(appraiserId, pageable)
                .map(invitationMapper::toResponse);
    }

  
    @Transactional
    public void accept(UUID invitationId, UUID appraiserId) {
        AppraisalInvitation invitation = invitationRepository
                .findByIdAndAppraiserId(invitationId, appraiserId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Invitation not found: " + invitationId));

        validatePendingAndNotExpired(invitation);

        AppraisalRequest request = findRequestById(
                invitation.getAppraisalRequestId());

        if (!request.isOpen()) {
            throw new IllegalStateException(
                    "This appraisal request is no longer available");
        }

        assignmentService.createFromInvitation(invitation, appraiserId);

        invitation.accept();
    }

   
    @Transactional
    public void reject(UUID invitationId, UUID appraiserId) {
        AppraisalInvitation invitation = invitationRepository
                .findByIdAndAppraiserId(invitationId, appraiserId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Invitation not found: " + invitationId));

        validatePendingAndNotExpired(invitation);
        invitation.reject();
    }


    @Transactional
    public void cancel(UUID invitationId, UUID sellerId) {
        AppraisalInvitation invitation = findInvitationById(invitationId);

        AppraisalRequest request = findRequestById(
                invitation.getAppraisalRequestId());

        if (!request.isSeller(sellerId)) {
            throw new AccessDeniedException(
                    "You are not allowed to cancel this invitation");
        }

        if (!invitation.isPending()) {
            throw new IllegalStateException(
                    "Only pending invitations can be cancelled");
        }

        invitation.cancel();
    }

    private void validatePendingAndNotExpired(AppraisalInvitation invitation) {
        if (!invitation.isPending()) {
            throw new IllegalStateException(
                    "Only pending invitations can be processed");
        }

        if (!invitation.getDeadline().isAfter(Instant.now())) {
            invitation.expire();

            throw new IllegalStateException(
                    "This invitation has expired");
        }
    }

    private AppraisalRequest findRequestById(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Appraisal request not found: " + requestId));
    }

    private AppraisalInvitation findInvitationById(UUID invitationId) {
        return invitationRepository.findById(invitationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Invitation not found: " + invitationId));
    }
}
