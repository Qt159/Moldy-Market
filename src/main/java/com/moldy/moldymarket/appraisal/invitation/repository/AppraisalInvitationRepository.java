
package com.moldy.moldymarket.appraisal.invitation.repository;

import com.moldy.moldymarket.appraisal.invitation.entity.AppraisalInvitation;
import com.moldy.moldymarket.appraisal.invitation.enums.AppraisalInvitationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppraisalInvitationRepository
        extends JpaRepository<AppraisalInvitation, UUID> {

    boolean existsByAppraisalRequestIdAndAppraiserId(UUID appraisalRequestId, UUID appraiserId);

    Page<AppraisalInvitation> findAllByAppraiserId(UUID appraiserId, Pageable pageable);

    Page<AppraisalInvitation> findAllByAppraisalRequestId(UUID appraisalRequestId, Pageable pageable);

    Optional<AppraisalInvitation> findByIdAndAppraiserId(UUID id, UUID appraiserId);

    List<AppraisalInvitation> findAllByAppraisalRequestIdAndStatus(
            UUID appraisalRequestId,
            AppraisalInvitationStatus status);

    long countByAppraisalRequestIdAndStatus(
            UUID appraisalRequestId,
            AppraisalInvitationStatus status);
}
