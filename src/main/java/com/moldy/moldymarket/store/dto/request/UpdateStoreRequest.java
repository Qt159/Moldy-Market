package com.moldy.moldymarket.store.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record UpdateStoreRequest(

    @NotBlank(message = "Store name must not be blank")
    @Size(max = 255)
    String name,

    String description,

    String pickupAddress
) {
}
