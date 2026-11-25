package com.moldy.moldymarket.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateProfileRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must not exceed 100 characters")
        String fullName,

        @Pattern(regexp = "^(\\+84|0)[0-9]{9}$", message = "Invalid Vietnamese phone number")
        String phone,

        @Size(max = 1000, message = "Avatar URL must not exceed 1000 characters")
        String avatarUrl,

        UUID avatarFileId,

        @Size(max = 500, message = "Address must not exceed 500 characters")
        String address
) {
    public UpdateProfileRequest(String fullName, String phone) {
        this(fullName, phone, null, null, null);
    }

    public UpdateProfileRequest(String fullName, String phone, String avatarUrl) {
        this(fullName, phone, avatarUrl, null, null);
    }

    public UpdateProfileRequest(String fullName, String phone, String avatarUrl, UUID avatarFileId) {
        this(fullName, phone, avatarUrl, avatarFileId, null);
    }
}
