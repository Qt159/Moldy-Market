package com.moldy.moldymarket.appraisal.request.mapper;

import com.moldy.moldymarket.appraisal.request.dto.request.CreateAppraisalRequestDto;
import com.moldy.moldymarket.appraisal.request.dto.response.AppraisalRequestResponse;
import com.moldy.moldymarket.appraisal.request.entity.AppraisalRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface AppraisalRequestMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sellerId", source = "sellerId")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AppraisalRequest toEntity(
            CreateAppraisalRequestDto dto,
            UUID sellerId
    );

    AppraisalRequestResponse toResponse(AppraisalRequest entity);
}
