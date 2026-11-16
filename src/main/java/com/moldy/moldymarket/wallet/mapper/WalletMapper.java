package com.moldy.moldymarket.wallet.mapper;

import com.moldy.moldymarket.wallet.dto.response.WalletResponse;
import com.moldy.moldymarket.wallet.entity.Wallet;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface WalletMapper {
    WalletResponse toWalletResponse(Wallet wallet);

//    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
//    void updateWallet(@MappingTarget Wallet wallet, WalletUpdateRequest request);
}
