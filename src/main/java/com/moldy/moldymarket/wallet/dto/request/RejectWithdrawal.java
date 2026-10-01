package com.moldy.moldymarket.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RejectWithdrawal(@NotBlank String reason){}