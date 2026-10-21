package com.moldy.moldymarket.store.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdateStaffPermissionsRequest(

        @NotNull(message = "Permission must not be null")
        Set<String> permissions
){
}
