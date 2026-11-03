package com.helmifr.library.book;

import com.helmifr.library.author.AuthorService;
import com.helmifr.library.book.CopyService.Availability;
import com.helmifr.library.common.ConflictException;
import com.helmifr.library.common.FieldValidationException;
import com.helmifr.library.common.NotFoundException;
import com.helmifr.library.common.PageResponse;
import com.helmifr.library.common.SearchPatterns;
import com.helmifr.library.loan.LoanRepository;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository books;
    private final LoanRepository loans;
    private final AuthorService authorService;
    private final CopyService copyService;
    private final CopyRepository copies;
    private final CatalogueProperties catalogue;

    /** Catalogue filters; null/false means "no filter". */
    public record Filter(String q, String category, Long authorId, boolean availableOnly, BookOrder order) {}

    public PageResponse<BookResponse> search(Filter filter, Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            throw new FieldValidationException("sort", "Books are sorted with order= (added, title, newest, oldest)");
        }
        if (filter.category() != null && !BookCategories.ALL.contains(filter.category())) {
            throw new FieldValidationException("category", "Unknown category: " + filter.category());
        }
        Page<Book> page = books.search(SearchPatterns.contains(filter.q()),
                filter.category() == null ? null : "%," + filter.category() + ",%",
                filter.authorId(), filter.availableOnly(),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), filter.order().sort()));
        Map<Long, Availability> availability = availabilityOf(page.getContent());
        return PageResponse.of(page.map(b -> BookResponse.from(b, availability.get(b.getId()), catalogue.coverUrl(b.getIsbn()))));
    }

    public List<BookResponse> findByAuthor(Long authorId) {
        List<Book> list = books.findAllByAuthorIdAndDeletedFalseOrderByTitleAsc(authorId);
        Map<Long, Availability> availability = availabilityOf(list);
        return list.stream().map(b -> BookResponse.from(b, availability.get(b.getId()), catalogue.coverUrl(b.getIsbn()))).toList();
    }

    public List<BookOption> options() {
        List<Book> list = books.findAllByDeletedFalseOrderByTitleAsc();
        Map<Long, Availability> availability = availabilityOf(list);
        return list.stream()
                .map(b -> {
                    Availability a = availability.get(b.getId());
                    return new BookOption(b.getId(), b.getTitle(), b.getAuthor().getName(), a.available(), a.total(),
                            a.allOut());
                })
                .toList();
    }

    /** Every category, or (inUse) only those some book in the catalogue has, A-Z. */
    public List<String> categories(boolean inUse) {
        if (!inUse) {
            return BookCategories.ALL;
        }
        return books.findAllCategoryStrings().stream()
                .flatMap(s -> Arrays.stream(s.split(",")))
                .map(String::trim)
                .filter(c -> !c.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }

    public BookResponse get(Long id) {
        Book book = require(id);
        return BookResponse.from(book, copyService.availability(id), catalogue.coverUrl(book.getIsbn()),
                copies.findShelfLocations(id), loans.countWaitingFor(id));
    }

    @Transactional
    public BookResponse create(BookRequest request) {
        Book book = new Book();
        apply(book, request);
        book = books.save(book);
        copyService.addGenerated(book, request.copies() == null ? 1 : request.copies());
        return view(book);
    }

    @Transactional
    public BookResponse update(Long id, BookRequest request) {
        Book book = require(id);
        apply(book, request);
        return view(book);
    }

    @Transactional
    public void delete(Long id) {
        Book book = require(id);
        if (loans.isBookOnLoan(id, null)) {
            throw new ConflictException("Book is currently on loan. Record its return before deleting it.");
        }
        book.setDeleted(true);
        copyService.withdrawAllOf(id);
    }

    private BookResponse view(Book book) {
        return BookResponse.from(book, copyService.availability(book.getId()), catalogue.coverUrl(book.getIsbn()));
    }

    private Book require(Long id) {
        return books.findByIdAndDeletedFalse(id).orElseThrow(() -> new NotFoundException("Book", id));
    }

    private void apply(Book book, BookRequest request) {
        List<String> categories = request.categories() == null ? List.of()
                : List.copyOf(new LinkedHashSet<>(request.categories().stream().map(String::trim).toList()));
        for (String category : categories) {
            if (!BookCategories.ALL.contains(category)) {
                throw new FieldValidationException("categories", "Unknown category: " + category);
            }
        }
        book.setTitle(request.title().trim());
        book.setPublishedDate(request.publishedDate());
        book.setPublisherName(blankToNull(request.publisherName()));
        book.setAuthor(authorService.require(request.authorId()));
        book.setCategoryList(categories);
        book.setIsbn(isbnOf(book, request.isbn()));
        book.setDescription(blankToNull(request.description()));
    }

    /** The ISBN-13 for the form's ISBN; blank clears it; another active book with it is refused. */
    private String isbnOf(Book book, String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String isbn = Isbn.toIsbn13(input).orElseThrow(
                () -> new FieldValidationException("isbn", "is not a valid ISBN-10 or ISBN-13"));
        boolean taken = book.getId() == null ? books.existsByIsbnAndDeletedFalse(isbn)
                : books.existsByIsbnAndDeletedFalseAndIdNot(isbn, book.getId());
        if (taken) {
            throw new FieldValidationException("isbn", "Another book already has this ISBN");
        }
        return isbn;
    }

    private Map<Long, Availability> availabilityOf(List<Book> list) {
        return copyService.availability(list.stream().map(Book::getId).toList());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
