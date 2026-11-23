package com.moldy.moldymarket.wallet.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


import java.math.BigDecimal;

public record WithdrawalRequest(
   @NotNull
   @DecimalMin(value = "0.01", message = "So tien rut phai lon hon 0")
   BigDecimal amount,

   @NotBlank
   String idempotencyKey){ }