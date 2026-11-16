package com.moldy.moldymarket.wallet.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawalRequest(
   UUID userId,

   @NotNull
   @DecimalMin(value = "0.01", message = "So tien rut phai lon hon 0")
   @Digits(integer = 17, fraction = 2)
   BigDecimal amount,

   @NotBlank
   @Size(max = 100)
   String bankName,

   @NotBlank
   @Size(max = 50)
   String bankAccountNumber,

   @NotBlank
   @Size(max = 150)
   String bankAccountHolder,

   @NotBlank
   @Size(max = 64)
   String idempotencyKey){ }
