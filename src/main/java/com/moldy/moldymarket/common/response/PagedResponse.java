package com.moldy.moldymarket.common.response;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;

public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        boolean hasNext,
        Long totalElements // null khi dùng Slice (không count), có giá trị khi dùng Page
) {
    // Factory method dùng chung - nhận Slice từ DB + list content đã map
    public static <T, E> PagedResponse<T> of(Slice<E> slice,
                                             List<T> content,
                                             Pageable pageable) {
        return new PagedResponse<>(
                content,
                pageable.getPageNumber() + 1,
                pageable.getPageSize(),
                slice.hasNext(),
                null
        );
    }

    public static <T, E> PagedResponse<T> of(Page<E> page, List<T> content, Pageable pageable) {
        return new PagedResponse<>(content, pageable.getPageNumber() + 1, pageable.getPageSize(), page.hasNext(), page.getTotalElements());
    }
}
