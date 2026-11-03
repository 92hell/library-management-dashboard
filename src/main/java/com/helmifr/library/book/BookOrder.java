package com.helmifr.library.book;

import com.helmifr.library.common.FieldValidationException;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.data.domain.Sort;

/** Catalogue sort orders (?order=); ADDED (newest additions first) is the default. */
public enum BookOrder {
    ADDED(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
    TITLE(Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id"))),
    NEWEST(Sort.by(Sort.Order.desc("publishedDate"), Sort.Order.asc("title"))),
    OLDEST(Sort.by(Sort.Order.asc("publishedDate"), Sort.Order.asc("title")));

    private final Sort sort;

    BookOrder(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }

    public static BookOrder parse(String value) {
        if (value == null || value.isBlank()) {
            return ADDED;
        }
        return Arrays.stream(values())
                .filter(o -> o.name().equals(value.trim().toUpperCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new FieldValidationException("order", "must be one of added, title, newest, oldest"));
    }
}
