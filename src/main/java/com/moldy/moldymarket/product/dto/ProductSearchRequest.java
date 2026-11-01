package com.moldy.moldymarket.product.dto;

import com.moldy.moldymarket.product.entity.ProductCondition;
import com.moldy.moldymarket.product.search.SellerType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.SqlReturnType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductSearchRequest (
    @Size(max = 100, message = "q must not exceed 100 characters")
    String q,

    @Size(max = 20, message = "too many categoryId values")
    List<UUID> categoryId,

    @Size(max = 10, message = "too many condition values")
    List<ProductCondition> condition,

    @DecimalMin(value = "0", message = "minPrice must not be negative")
    BigDecimal minPrice,

    @DecimalMin(value = "0", message = "maxPrice must not be negative")
    BigDecimal maxPrice,

    UUID storeId,

    SellerType sellerType,

    @Size(max = 10, message = "too many attr values")
    List<String> attr,

    String sort,

    Boolean facets

) {}
