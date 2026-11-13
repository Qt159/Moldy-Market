package com.moldy.moldymarket.appraiser.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterAppraiserRequest {
    
    @NotBlank(message = "Bio cannot be blank")
    @Size(min = 10, max = 1000,
         message = "Bio must be between 10 and 1000 characters")
    private String bio;
    
    @NotEmpty(message = "At least one category is required")
    @Size(max = 10, message = "Maximum 10 categories allowed")
    private List<UUID> categoryIds;
    
    @Size(max = 255, message = "Avatar URL cannot exceed 255 characters")
    private String avatarUrl;
}
