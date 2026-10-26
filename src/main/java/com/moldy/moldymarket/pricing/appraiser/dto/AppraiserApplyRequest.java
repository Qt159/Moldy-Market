package com.moldy.moldymarket.pricing.appraiser.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record AppraiserApplyRequest(
    @NotEmpty(message = "Category IDs must not be empty")
    List<UUID> categoryIds
) {}
