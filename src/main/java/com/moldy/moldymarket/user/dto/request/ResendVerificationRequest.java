package com.moldy.moldymarket.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendVerificationRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        String email
) {
}
