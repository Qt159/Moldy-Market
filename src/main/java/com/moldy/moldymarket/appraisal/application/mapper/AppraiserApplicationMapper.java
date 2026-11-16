package com.moldy.moldymarket.appraisal.application.mapper;

import com.moldy.moldymarket.appraisal.application.dto.request.CreateApplicationDto;
import com.moldy.moldymarket.appraisal.application.dto.response.AppraiserApplicationResponse;
import com.moldy.moldymarket.appraisal.application.entity.AppraiserApplication;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface AppraiserApplicationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "appraisalRequestId", source = "requestId")
    @Mapping(target = "appraiserId", source = "appraiserId")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "appliedAt", ignore = true)
    @Mapping(target = "respondedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    AppraiserApplication toEntity(CreateApplicationDto dto, UUID requestId, UUID appraiserId);

    AppraiserApplicationResponse toResponse(AppraiserApplication entity);
}
