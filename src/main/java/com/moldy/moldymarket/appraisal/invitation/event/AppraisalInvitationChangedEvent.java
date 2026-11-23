package com.moldy.moldymarket.appraisal.invitation.event;

import com.moldy.moldymarket.appraisal.invitation.enums.AppraisalInvitationStatus;

import java.util.UUID;

public record AppraisalInvitationChangedEvent(
        UUID invitationId,
        UUID appraisalRequestId,
        UUID sellerId,
        UUID appraiserId,
        UUID actorId,
        AppraisalInvitationStatus status) {
}
