package com.moldy.moldymarket.wallet.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawalRequest(
   @NotNull
   UUID userId,

   @NotNull
   @DecimalMin(value = "0.01", message = "So tien rut phai lon hon 0")
   BigDecimal amount,

   @NotBlank
   String bankName,

   @NotBlank
   String bankAccountNumber,

   @NotBlank
   String bankAccountHolder,

   @NotBlank
   String idempotencyKey){ }