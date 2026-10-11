package com.eventhub.api.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 分页响应的稳定契约。刻意不直接暴露 Spring Data 的 {@code Page}，
 * 因为它的 JSON 结构不稳定（Spring Boot 3.3+ 起有告警，4.x 会变）。
 */
@Schema(description = "分页结果")
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }

    public static <T> PageResponse<T> empty(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0L, 0, false);
    }
}
