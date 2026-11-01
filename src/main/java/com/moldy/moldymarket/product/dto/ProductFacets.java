package com.moldy.moldymarket.product.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductFacets(
        List<CategoryFacet> categories,
        List<ConditionFacet> conditions,
        List<PriceRangeFacet> priceRanges
) {
    /** Root có children là các sub; sub có children rỗng. count của root = tổng các sub. */
    public record CategoryFacet(UUID id, String name, long count, List<CategoryFacet> children) {}

    public record ConditionFacet(String value, long count) {}

    /** Khoảng [min, max): max = null nghĩa là không giới hạn trên. */
    public record PriceRangeFacet(BigDecimal min, BigDecimal max, long count) {}
}
