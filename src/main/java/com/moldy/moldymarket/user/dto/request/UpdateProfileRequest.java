package com.moldy.moldymarket.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must not exceed 100 characters")
        String fullName,

        @Pattern(regexp = "^(\\+84|0)[0-9]{9}$", message = "Invalid Vietnamese phone number")
        String phone,

        @Size(max = 1000, message = "Avatar URL must not exceed 1000 characters")
        String avatarUrl
) {
    public UpdateProfileRequest(String fullName, String phone) {
        this(fullName, phone, null);
    }
}
