package com.helmifr.library.author;

import com.helmifr.library.book.BookResponse;
import com.helmifr.library.book.BookService;
import com.helmifr.library.common.PageResponse;
import com.helmifr.library.common.Ref;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/authors")
@RequiredArgsConstructor
public class AuthorController {

    private final AuthorService authorService;
    private final BookService bookService;

    @GetMapping
    public PageResponse<AuthorResponse> list(@RequestParam(required = false) String q,
                                             @PageableDefault(size = 20) Pageable pageable) {
        return authorService.search(q, pageable);
    }

    @GetMapping("/options")
    public List<Ref> options() {
        return authorService.options();
    }

    @GetMapping("/{id}")
    public AuthorResponse get(@PathVariable Long id) {
        return authorService.get(id);
    }

    @GetMapping("/{id}/books")
    public List<BookResponse> books(@PathVariable Long id) {
        authorService.require(id);
        return bookService.findByAuthor(id);
    }

    @PostMapping
    public ResponseEntity<AuthorResponse> create(@Valid @RequestBody AuthorRequest request) {
        AuthorResponse created = authorService.create(request);
        return ResponseEntity.created(URI.create("/api/authors/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public AuthorResponse update(@PathVariable Long id, @Valid @RequestBody AuthorRequest request) {
        return authorService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        authorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
