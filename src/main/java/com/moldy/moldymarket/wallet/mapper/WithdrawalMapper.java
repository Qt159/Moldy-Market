package com.moldy.moldymarket.wallet.mapper;

import com.moldy.moldymarket.wallet.dto.request.WithdrawalRequest;
import com.moldy.moldymarket.wallet.dto.response.WithdrawalResponse;
import com.moldy.moldymarket.wallet.dto.response.AdminWithdrawalResponse;
import com.moldy.moldymarket.wallet.entity.Withdrawal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface WithdrawalMapper {
    Withdrawal toWithdrawal(WithdrawalRequest request);

    @Mapping(target = "maskedBankAccountNumber", source = "bankAccountNumber", qualifiedByName = "maskBankAccount")
    WithdrawalResponse toWithdrawalResponse(Withdrawal withdrawal);

    AdminWithdrawalResponse toAdminWithdrawalResponse(Withdrawal withdrawal);

    @Named("maskBankAccount")
    default String maskBankAccount(String account) {
        if (account == null || account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }
}
