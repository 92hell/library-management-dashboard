package com.helmifr.library.book;

import com.helmifr.library.common.Ref;
import java.time.LocalDate;
import java.util.List;

public record BookResponse(Long id, String title, LocalDate publishedDate, String publisherName,
                           Ref author, List<String> categories, boolean onLoan) {
    static BookResponse from(Book b, boolean onLoan) {
        return new BookResponse(b.getId(), b.getTitle(), b.getPublishedDate(), b.getPublisherName(),
                new Ref(b.getAuthor().getId(), b.getAuthor().getName()), b.getCategoryList(), onLoan);
    }
}
