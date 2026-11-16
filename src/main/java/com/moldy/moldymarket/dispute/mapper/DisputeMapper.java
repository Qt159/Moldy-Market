package com.moldy.moldymarket.dispute.mapper;

import com.moldy.moldymarket.dispute.dto.response.DisputeResponse;
import com.moldy.moldymarket.dispute.dto.response.DisputeSummaryResponse;
import com.moldy.moldymarket.dispute.entity.DisputeRequest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DisputeMapper {

    DisputeResponse toDisputeResponse(DisputeRequest request);
    DisputeSummaryResponse toDisputeSummaryResponse(DisputeRequest disputeRequest);
}
