package com.moldy.moldymarket.category.dto;

import com.moldy.moldymarket.category.entity.CategoryStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String description,
        UUID parentId,
        int level,
        CategoryStatus status,
        boolean hasChildren,
        List<CategoryResponse> children,
        Instant createdAt,
        Instant updatedAt
) {}