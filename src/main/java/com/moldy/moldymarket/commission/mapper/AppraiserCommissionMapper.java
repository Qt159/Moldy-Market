package com.moldy.moldymarket.commission.mapper;

import com.moldy.moldymarket.commission.dto.response.AppraiserCommissionResponse;
import com.moldy.moldymarket.commission.entity.AppraiserCommission;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AppraiserCommissionMapper {

    AppraiserCommissionResponse toResponse(
        AppraiserCommission commission);
}