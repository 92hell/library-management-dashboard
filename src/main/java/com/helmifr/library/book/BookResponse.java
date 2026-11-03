package com.helmifr.library.book;

import com.helmifr.library.book.CopyService.Availability;
import com.helmifr.library.common.Ref;
import java.time.LocalDate;
import java.util.List;

/**
 * @param totalCopies     copies in the collection
 * @param availableCopies copies on the shelf right now
 * @param onLoan          every copy is out (kept for clients that only need a yes/no)
 * @param coverUrl        cover image (Open Library) for books with an ISBN; the browser falls back if it is missing
 * @param shelfLocations  where the copies are shelved; only on the single-book endpoint (null in lists)
 * @param waiting         requests waiting for this title; only on the single-book endpoint (null in lists)
 */
public record BookResponse(Long id, String title, LocalDate publishedDate, String publisherName,
                           Ref author, List<String> categories, long totalCopies, long availableCopies,
                           boolean onLoan, String isbn, String description, String coverUrl,
                           List<String> shelfLocations, Long waiting) {

    static BookResponse from(Book b, Availability availability, String coverUrl) {
        return from(b, availability, coverUrl, null, null);
    }

    static BookResponse from(Book b, Availability availability, String coverUrl, List<String> shelfLocations,
                             Long waiting) {
        return new BookResponse(b.getId(), b.getTitle(), b.getPublishedDate(), b.getPublisherName(),
                new Ref(b.getAuthor().getId(), b.getAuthor().getName()), b.getCategoryList(),
                availability.total(), availability.available(), availability.allOut(), b.getIsbn(),
                b.getDescription(), coverUrl, shelfLocations, waiting);
    }
}
