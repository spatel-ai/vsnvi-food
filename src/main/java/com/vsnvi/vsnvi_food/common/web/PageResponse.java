package com.vsnvi.vsnvi_food.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Offset pagination envelope from {@code docs/API-DESIGN.md} §5. */
public record PageResponse<T>(List<T> content, PageMetadata page) {

    public static final int MAX_PAGE_SIZE = 100;

    public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().<T>map(mapper).toList(),
                new PageMetadata(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
    }

    public record PageMetadata(int number, int size, long totalElements, int totalPages) {}
}
