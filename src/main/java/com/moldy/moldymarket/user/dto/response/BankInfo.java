package com.moldy.moldymarket.user.dto.response;

import lombok.Builder;

@Builder
public record BankInfo(
        String bankName,
        String bankAccountNumber
) {
}
