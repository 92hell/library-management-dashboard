package com.helmifr.library.book;

import com.helmifr.library.author.AuthorService;
import com.helmifr.library.common.ConflictException;
import com.helmifr.library.common.FieldValidationException;
import com.helmifr.library.common.NotFoundException;
import com.helmifr.library.common.PageResponse;
import com.helmifr.library.common.SearchPatterns;
import com.helmifr.library.loan.LoanRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

    public PageResponse<BookResponse> search(String q, Pageable pageable) {
        Page<Book> page = books.search(SearchPatterns.contains(q), pageable);
        Set<Long> onLoan = booksOnLoan(page.getContent());
        return PageResponse.of(page.map(b -> BookResponse.from(b, onLoan.contains(b.getId()))));
    }

    public List<BookResponse> findByAuthor(Long authorId) {
        List<Book> list = books.findAllByAuthorIdAndDeletedFalseOrderByTitleAsc(authorId);
        Set<Long> onLoan = booksOnLoan(list);
        return list.stream().map(b -> BookResponse.from(b, onLoan.contains(b.getId()))).toList();
    }

    public List<BookOption> options() {
        List<Book> list = books.findAllByDeletedFalseOrderByTitleAsc();
        Set<Long> onLoan = booksOnLoan(list);
        return list.stream()
                .map(b -> new BookOption(b.getId(), b.getTitle(), b.getAuthor().getName(), onLoan.contains(b.getId())))
                .toList();
    }

    public List<String> categories() {
        return BookCategories.ALL;
    }

    public BookResponse get(Long id) {
        Book book = require(id);
        return BookResponse.from(book, loans.isBookOnLoan(id, null));
    }

    @Transactional
    public BookResponse create(BookRequest request) {
        Book book = new Book();
        apply(book, request);
        return BookResponse.from(books.save(book), false);
    }

    @Transactional
    public BookResponse update(Long id, BookRequest request) {
        Book book = require(id);
        apply(book, request);
        return BookResponse.from(book, loans.isBookOnLoan(id, null));
    }

    @Transactional
    public void delete(Long id) {
        Book book = require(id);
        if (loans.isBookOnLoan(id, null)) {
            throw new ConflictException("Book is currently on loan. Record its return before deleting it.");
        }
        book.setDeleted(true);
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
    }

    private Set<Long> booksOnLoan(List<Book> list) {
        if (list.isEmpty()) {
            return Set.of();
        }
        return loans.findBookIdsOnLoan(list.stream().map(Book::getId).toList());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
