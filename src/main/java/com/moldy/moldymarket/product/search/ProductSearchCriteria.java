package com.moldy.moldymarket.product.search;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductSearchCriteria(
        String normalizedQuery, // Các từ đã nối lại, rỗng nếu không có từ khóa
        List<String> tokens,
        List<UUID> categoryIds,
        List<String> conditions, // tên enum
        BigDecimal minPrice,
        BigDecimal maxPrice,
        UUID storeId,
        SellerType sellerType,
        Map<String, List<String>> attributes, // khóa -> các giá trị (OR trong cùng khóa)
        ProductSortOption sort
) {
}
