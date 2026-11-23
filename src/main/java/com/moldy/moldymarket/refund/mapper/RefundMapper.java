package com.moldy.moldymarket.refund.mapper;

import com.moldy.moldymarket.refund.dto.response.RefundResponse;
import com.moldy.moldymarket.refund.entity.RefundRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RefundMapper {

    @Mapping(target = "refundId", source = "id")
    RefundResponse toRefundResponse(RefundRequest refund);
}
