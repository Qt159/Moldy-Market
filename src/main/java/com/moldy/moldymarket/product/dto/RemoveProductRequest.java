package com.moldy.moldymarket.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RemoveProductRequest(
        @NotBlank(message = "Reason must not be blank")
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason
) {
}
