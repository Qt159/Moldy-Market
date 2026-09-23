package com.moldy.moldymarket.store.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateStoreRequest {

    @NotBlank(message = "Store name must not be blank")
    @Size(max = 255)
    String name;

    String description;

    String pickupAddress;
}
