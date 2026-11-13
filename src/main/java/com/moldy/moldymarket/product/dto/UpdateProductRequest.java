package com.moldy.moldymarket.product.dto;

import com.moldy.moldymarket.product.entity.ProductCondition;
import com.moldy.moldymarket.product.validation.ValidProductAttributes;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;


public record UpdateProductRequest(
        @Size(max = 150)
        @Pattern(regexp = ".*\\S.*", message = "title must not be blank")
        String title,

        @Size(max = 2000) String description,

        @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
        @DecimalMax(value = "1000000000", message = "Price must not exceed 1,000,000,000")
        @Digits(integer = 10, fraction = 2, message = "Price can have at most 2 decimal places")
        BigDecimal price,

        ProductCondition condition,

        @Size(max = 10, message = "A product can have at most 10 images")
        List<@NotBlank @Size(max = 500, message = "Image link is too long")
        @Pattern(regexp = "^https://\\S+$", message = "Image must be an https link") String> images,

        @ValidProductAttributes
        Map<String, Object> attributes,

        @NotNull(message = "Version is required")
        Long version
) {
}
