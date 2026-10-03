package com.moldy.moldymarket.common.response;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;

public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        boolean hasNext
) {
    // Factory method dùng chung - nhận Slice từ DB + list content đã map
    public static <T, E> PagedResponse<T> of(Slice<E> slice,
                                             List<T> content,
                                             Pageable pageable) {
        return new PagedResponse<>(
                content,
                pageable.getPageNumber() + 1,
                pageable.getPageSize(),
                slice.hasNext()
        );
    }
}
