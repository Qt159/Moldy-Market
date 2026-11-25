package com.moldy.moldymarket.wallet.mapper;

import com.moldy.moldymarket.wallet.dto.request.WithdrawalRequest;
import com.moldy.moldymarket.wallet.dto.response.WithdrawalResponse;
import com.moldy.moldymarket.wallet.entity.Withdrawal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface WithdrawalMapper {
    Withdrawal toWithdrawal(WithdrawalRequest request);

    @Mapping(target = "maskedBankAccountNumber", source = "bankAccountNumber", qualifiedByName = "maskAccountNumber")
    WithdrawalResponse toWithdrawalResponse(Withdrawal withdrawal);

    @Named("maskAccountNumber")
    default String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return null;
        }
        String trimmed = accountNumber.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }
}
