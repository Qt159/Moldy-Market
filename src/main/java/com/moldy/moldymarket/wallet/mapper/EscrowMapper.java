package com.moldy.moldymarket.wallet.mapper;

import com.moldy.moldymarket.wallet.dto.response.EscrowHoldResponse;
import com.moldy.moldymarket.wallet.entity.EscrowHold;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EscrowMapper {
    EscrowHoldResponse toEscrowHoldResponse(EscrowHold escrowHold);
}
