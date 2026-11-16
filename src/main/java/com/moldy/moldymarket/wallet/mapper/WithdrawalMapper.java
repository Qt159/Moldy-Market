package com.moldy.moldymarket.wallet.mapper;

import com.moldy.moldymarket.wallet.dto.request.WithdrawalRequest;
import com.moldy.moldymarket.wallet.dto.response.WithdrawalResponse;
import com.moldy.moldymarket.wallet.entity.Withdrawal;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WithdrawalMapper {
    Withdrawal toWithdrawal(WithdrawalRequest request);

    WithdrawalResponse toWithdrawalResponse(Withdrawal withdrawal);
}
