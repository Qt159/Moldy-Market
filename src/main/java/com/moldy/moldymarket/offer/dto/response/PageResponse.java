package com.moldy.moldymarket.offer.dto.response;


import lombok.Builder;

import java.util.Collections;
import java.util.List;

@Builder
public record PageResponse<T>(
        int currentPage,
        int totalPages,
        int pageSize,
        long totalElements,
        List<T> data
) {
    public PageResponse{
        if(data == null) data = Collections.emptyList();
    }
}
