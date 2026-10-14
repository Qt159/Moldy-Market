package com.moldy.moldymarket.store.dto.request;

import com.moldy.moldymarket.store.entity.StoreStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


public record UpdateStoreStatusRequest(

    @NotNull(message = "Status must not be null")
    StoreStatus status

) {
}
