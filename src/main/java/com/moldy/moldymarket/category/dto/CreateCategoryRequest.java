package com.moldy.moldymarket.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCategoryRequest(

        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must not exceed 100 characters")
        String name,

        @Size(max = 500, message = "description must not exceed 500 characters")
        String description,

        // null = Root Category
        UUID parentId
) {}