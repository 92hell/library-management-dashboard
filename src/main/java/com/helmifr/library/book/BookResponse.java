package com.helmifr.library.book;

import com.helmifr.library.book.CopyService.Availability;
import com.helmifr.library.common.Ref;
import java.time.LocalDate;
import java.util.List;

/**
 * @param totalCopies     copies in the collection
 * @param availableCopies copies on the shelf right now
 * @param onLoan          every copy is out (kept for clients that only need a yes/no)
 */
public record BookResponse(Long id, String title, LocalDate publishedDate, String publisherName,
                           Ref author, List<String> categories, long totalCopies, long availableCopies,
                           boolean onLoan) {
    static BookResponse from(Book b, Availability availability) {
        return new BookResponse(b.getId(), b.getTitle(), b.getPublishedDate(), b.getPublisherName(),
                new Ref(b.getAuthor().getId(), b.getAuthor().getName()), b.getCategoryList(),
                availability.total(), availability.available(), availability.allOut());
    }
}
