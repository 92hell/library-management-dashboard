package com.helmifr.library.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable JSON shape for paged lists (Spring's Page serialization is not a stable contract). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }
}
