package com.moldy.moldymarket.product.dto;

import java.util.List;

public record ProductSearchResponse(
        List<ProductResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean totalCapped, // True có nghĩa là "có từ 10000 kết quả trở lên", totalElements chỉ là mức trần
        ProductFacets facets // null nếu không xin facets=true
) {
}
