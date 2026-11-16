
package com.moldy.moldymarket.appraisal.invitation.mapper;

import com.moldy.moldymarket.appraisal.invitation.dto.request.CreateInvitationDto;
import com.moldy.moldymarket.appraisal.invitation.dto.response.AppraisalInvitationResponse;
import com.moldy.moldymarket.appraisal.invitation.entity.AppraisalInvitation;
import org.mapstruct.Mapper;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface AppraisalInvitationMapper {

    default AppraisalInvitation toEntity(CreateInvitationDto dto, UUID requestId) {
        return new AppraisalInvitation(
                requestId,
                dto.appraiserId(),
                dto.proposedFee(),
                dto.message(),
                dto.deadline()
        );
    }
    AppraisalInvitationResponse toResponse(AppraisalInvitation entity);
}
