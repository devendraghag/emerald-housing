package com.emerald.society.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements,
                              int totalPages, String sortBy, String direction) {
    public PageResponse {
        content = List.copyOf(content);
    }

    public static <S, T> PageResponse<T> from(Page<S> source, Function<S, T> mapper,
                                               String sortBy, String direction) {
        return new PageResponse<>(source.map(mapper).getContent(), source.getNumber(), source.getSize(),
                source.getTotalElements(), source.getTotalPages(), sortBy, direction);
    }
}
