package com.moldy.moldymarket.appraisal.review.mapper;

import com.moldy.moldymarket.appraisal.review.dto.request.CreateAppraisalReviewDto;
import com.moldy.moldymarket.appraisal.review.dto.response.AppraisalReviewResponse;
import com.moldy.moldymarket.appraisal.review.entity.AppraisalReview;
import org.mapstruct.Mapper;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface AppraisalReviewMapper {

    default AppraisalReview toEntity(
            CreateAppraisalReviewDto dto,
            UUID assignmentId,
            UUID sellerId,
            UUID appraiserId) {
        return new AppraisalReview(
                assignmentId,
                sellerId,
                appraiserId,
                dto.rating(),
                dto.accuracyScore(),
                dto.professionalismScore(),
                dto.communicationScore(),
                dto.timelinessScore(),
                dto.comment(),
                dto.recommend());
    }

    AppraisalReviewResponse toResponse(AppraisalReview review);
}