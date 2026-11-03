package com.helmifr.library.book;

import com.helmifr.library.common.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;
    private final IsbnLookup isbnLookup;

    @GetMapping
    public PageResponse<BookResponse> list(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) String category,
                                           @RequestParam(required = false) Long authorId,
                                           @RequestParam(defaultValue = "false") boolean available,
                                           @RequestParam(required = false) String order,
                                           @PageableDefault(size = 20) Pageable pageable) {
        String cat = category == null || category.isBlank() ? null : category.trim();
        return bookService.search(new BookService.Filter(q, cat, authorId, available, BookOrder.parse(order)), pageable);
    }

    /** All categories, or with ?inUse=true only those some book has (for the catalogue filter). */
    @GetMapping("/categories")
    public List<String> categories(@RequestParam(defaultValue = "false") boolean inUse) {
        return bookService.categories(inUse);
    }

    @GetMapping("/options")
    public List<BookOption> options() {
        return bookService.options();
    }

    /** Librarians: fill the book form from Open Library by ISBN. */
    @GetMapping("/lookup")
    public IsbnLookup.Result lookup(@RequestParam String isbn) {
        return isbnLookup.lookup(isbn);
    }

    @GetMapping("/{id}")
    public BookResponse get(@PathVariable Long id) {
        return bookService.get(id);
    }

    @PostMapping
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
        BookResponse created = bookService.create(request);
        return ResponseEntity.created(URI.create("/api/books/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public BookResponse update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return bookService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bookService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
