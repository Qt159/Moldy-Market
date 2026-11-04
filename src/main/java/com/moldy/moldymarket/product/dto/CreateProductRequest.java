package com.moldy.moldymarket.product.dto;
import com.moldy.moldymarket.product.entity.PricingStrategy;
import com.moldy.moldymarket.product.entity.ProductCondition;
import com.moldy.moldymarket.product.validation.ValidProductAttributes;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateProductRequest(
        UUID storeId, // null = đăng bán cá nhân

        @NotNull
        UUID categoryId,

        @NotBlank
        @Size(max = 150)
        String title,

        @Size(max = 2000) String description,

        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
        @DecimalMax(value = "1000000000", message = "Price must not exceed 1,000,000,000")
        @Digits(integer = 10, fraction = 2, message = "Price can have at most 2 decimal places")
        BigDecimal price,

        @NotNull ProductCondition condition,

        @NotNull PricingStrategy pricingStrategy,

        @Size(max = 10, message = "A product can have at most 10 images")
        List<@NotBlank @Size(max = 500, message = "Image link is too long")
        @Pattern(regexp = "^https://\\S+$", message = "Image must be an https link") String> images,

        @ValidProductAttributes
        Map<String, Object> attributes
) {}
