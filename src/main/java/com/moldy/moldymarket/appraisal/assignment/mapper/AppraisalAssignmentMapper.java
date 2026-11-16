package com.moldy.moldymarket.appraisal.assignment.mapper;

import com.moldy.moldymarket.appraisal.assignment.dto.response.AppraisalAssignmentResponse;
import com.moldy.moldymarket.appraisal.assignment.entity.AppraisalAssignment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AppraisalAssignmentMapper {
    AppraisalAssignmentResponse toResponse(AppraisalAssignment assignment);
}