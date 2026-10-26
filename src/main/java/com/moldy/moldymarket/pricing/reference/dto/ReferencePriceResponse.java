package com.moldy.moldymarket.pricing.reference.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReferencePriceResponse(
    UUID id,
    UUID categoryId,
    String brand,
    String modelName,
    BigDecimal referencePrice,
    String currency,
    Instant updatedAt,
    UUID updatedBy,
    Instant createdAt
) {}
