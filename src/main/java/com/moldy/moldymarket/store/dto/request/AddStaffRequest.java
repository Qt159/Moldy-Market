package com.moldy.moldymarket.store.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record AddStaffRequest (

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be valid")
        String email,

        Set<String> permissions
) {}
