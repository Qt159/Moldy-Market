package com.moldy.moldymarket.appraisal.assignment.service;

import com.moldy.moldymarket.appraisal.assignment.dto.request.SelectAppraiserDto;
import com.moldy.moldymarket.appraisal.assignment.dto.response.AppraisalAssignmentResponse;
import com.moldy.moldymarket.appraisal.assignment.entity.AppraisalAssignment;
import com.moldy.moldymarket.appraisal.assignment.enums.AppraisalSourceType;
import com.moldy.moldymarket.appraisal.assignment.mapper.AppraisalAssignmentMapper;
import com.moldy.moldymarket.appraisal.assignment.repository.AppraisalAssignmentRepository;
import com.moldy.moldymarket.appraisal.application.entity.AppraiserApplication;
import com.moldy.moldymarket.appraisal.application.enums.AppraiserApplicationStatus;
import com.moldy.moldymarket.appraisal.application.repository.AppraiserApplicationRepository;
import com.moldy.moldymarket.appraisal.invitation.entity.AppraisalInvitation;
import com.moldy.moldymarket.appraisal.invitation.repository.AppraisalInvitationRepository;
import com.moldy.moldymarket.appraisal.request.entity.AppraisalRequest;
import com.moldy.moldymarket.appraisal.request.enums.AppraisalRequestStatus;
import com.moldy.moldymarket.appraisal.request.repository.AppraisalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppraisalAssignmentService {

    private final AppraisalAssignmentRepository assignmentRepository;
    private final AppraisalRequestRepository requestRepository;
    private final AppraiserApplicationRepository applicationRepository;
    private final AppraisalInvitationRepository invitationRepository;
    private final AppraisalAssignmentMapper assignmentMapper;

    // TODO: Inject service của module Chat khi thống nhất API tạo conversation.
    private final AppraisalConversationService conversationService;

  
    @Transactional
    public AppraisalAssignmentResponse selectAppraiser(UUID requestId, UUID sellerId, SelectAppraiserDto dto) {
        AppraisalRequest request = findRequestForUpdate(requestId);

        validateRequestOwner(request, sellerId);
        validateRequestOpen(request);

        AppraiserApplication application = applicationRepository
                .findById(dto.applicationId())
                .orElseThrow(() -> new RuntimeException("Appraiser application not found"));

        validateApplication(application, requestId);

        if (application.getStatus() != AppraiserApplicationStatus.PENDING) {
            throw new RuntimeException(
                    "Application is no longer available");
        }

        AppraisalAssignment assignment = new AppraisalAssignment(
                request.getId(),
                request.getSellerId(),
                application.getAppraiserId(),
                application.getId(),
                null,
                AppraisalSourceType.APPRAISER_APPLICATION,
                application.getProposedFee());

        return createAssignment(request, assignment, application);
    }

  
    @Transactional
    public AppraisalAssignmentResponse createFromInvitation(
            AppraisalInvitation invitation, UUID appraiserId) {
        AppraisalRequest request = findRequestForUpdate(
                invitation.getAppraisalRequestId());

        validateRequestOpen(request);

        if (!invitation.getAppraiserId().equals(appraiserId)) {
            throw new RuntimeException(
                    "You are not authorized to accept this invitation");
        }

        if (assignmentRepository.existsByAppraisalRequestId(request.getId())) {
            throw new RuntimeException(
                    "An assignment already exists for this request");
        }

        AppraisalAssignment assignment = new AppraisalAssignment(
                request.getId(),
                request.getSellerId(),
                appraiserId,
                null,
                invitation.getId(),
                AppraisalSourceType.SELLER_INVITATION,
                invitation.getProposedFee()
        );

        return createAssignment(request, assignment, null);
    }

   
    private AppraisalAssignmentResponse createAssignment(
            AppraisalRequest request, AppraisalAssignment assignment, AppraiserApplication application) {
        if (assignmentRepository.existsByAppraisalRequestId(request.getId())) {
            throw new RuntimeException(
                    "An assignment already exists for this request");
        }

        AppraisalAssignment savedAssignment =
                assignmentRepository.save(assignment);

        // Create a private conversation between seller and appraiser.
        UUID conversationId = conversationService.createConversation(
                request.getSellerId(),
                savedAssignment.getAppraiserId()
        );

        savedAssignment.assignConversation(conversationId);

        // Update request state.
        request.assign();

        // application đã được accepted.
        if (application != null) {
            application.accept();
        }

        return assignmentMapper.toResponse(savedAssignment);
    }

   
    public AppraisalAssignmentResponse getById(UUID assignmentId, UUID userId) {
        AppraisalAssignment assignment = findAssignmentById(assignmentId);

        validateParticipant(assignment, userId);

        return assignmentMapper.toResponse(assignment);
    }

    public Page<AppraisalAssignmentResponse> getMyAssignments(
                        UUID appraiserId, Pageable pageable) {
        return assignmentRepository
                .findAllByAppraiserId(appraiserId, pageable)
                .map(assignmentMapper::toResponse);
    }

    
    public Page<AppraisalAssignmentResponse> getSellerAssignments(
                            UUID sellerId, Pageable pageable) {
        return assignmentRepository
                .findAllBySellerId(sellerId, pageable)
                .map(assignmentMapper::toResponse);
    }

    
    @Transactional
    public AppraisalAssignmentResponse start(
            UUID assignmentId, UUID appraiserId) {
        AppraisalAssignment assignment = findAssignmentById(assignmentId);

        validateAppraiserOwner(assignment, appraiserId);

        assignment.start();

        AppraisalRequest request = findRequestById(
                assignment.getAppraisalRequestId());

        request.start();

        return assignmentMapper.toResponse(assignment);
    }


    @Transactional
    public AppraisalAssignmentResponse cancel(
                        UUID assignmentId, UUID userId) {
        AppraisalAssignment assignment = findAssignmentById(assignmentId);

        validateParticipant(assignment, userId);

        assignment.cancel();

        AppraisalRequest request = findRequestById(
                assignment.getAppraisalRequestId());

        request.cancel();

        return assignmentMapper.toResponse(assignment);
    }

    private AppraisalRequest findRequestForUpdate(UUID requestId) {
        return requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new RuntimeException(
                        "Appraisal request not found"));
    }

    private AppraisalRequest findRequestById(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException(
                        "Appraisal request not found"));
    }

    private AppraisalAssignment findAssignmentById(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException(
                        "Appraisal assignment not found"));
    }

    private void validateRequestOwner(
            AppraisalRequest request, UUID sellerId) {
        if (!request.isSeller(sellerId)) {
            throw new RuntimeException(
                    "You are not the owner of this appraisal request");
        }
    }

    private void validateRequestOpen(AppraisalRequest request) {
        if (request.getStatus() != AppraisalRequestStatus.OPEN) {
            throw new RuntimeException(
                    "Appraisal request is no longer open");
        }
    }

    private void validateApplication(
            AppraiserApplication application, UUID requestId) {
        if (!application.getAppraisalRequestId().equals(requestId)) {
            throw new RuntimeException(
                    "Application does not belong to this request");
        }
    }

    private void validateParticipant(
            AppraisalAssignment assignment, UUID userId) {
        boolean isSeller = assignment.getSellerId().equals(userId);
        boolean isAppraiser = assignment.getAppraiserId().equals(userId);

        if (!isSeller && !isAppraiser) {
            throw new RuntimeException(
                    "You are not allowed to access this assignment");
        }
    }

    private void validateAppraiserOwner(
            AppraisalAssignment assignment, UUID appraiserId) {
        if (!assignment.getAppraiserId().equals(appraiserId)) {
            throw new RuntimeException(
                    "You are not the assigned appraiser");
        }
    }
}