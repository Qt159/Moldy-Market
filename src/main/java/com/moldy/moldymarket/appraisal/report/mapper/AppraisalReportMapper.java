package com.moldy.moldymarket.appraisal.report.mapper;

import com.moldy.moldymarket.appraisal.report.dto.request.SubmitAppraisalReportDto;
import com.moldy.moldymarket.appraisal.report.dto.response.AppraisalReportResponse;
import com.moldy.moldymarket.appraisal.report.entity.AppraisalReport;
import org.mapstruct.Mapper;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface AppraisalReportMapper {

    default AppraisalReport toEntity(
            SubmitAppraisalReportDto dto,
            UUID assignmentId,
            UUID appraiserId,
            Integer revisionNumber
    ) {
        return new AppraisalReport(
                assignmentId,
                appraiserId,
                revisionNumber,
                dto.description(),
                dto.conditionAssessment(),
                dto.assessedValue(),
                dto.imageUrls(),
                dto.documentUrls()
        );
    }

    AppraisalReportResponse toResponse(AppraisalReport report);
}