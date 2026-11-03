package com.helmifr.library.author;

import com.helmifr.library.book.BookRepository;
import com.helmifr.library.common.ConflictException;
import com.helmifr.library.common.NotFoundException;
import com.helmifr.library.common.PageResponse;
import com.helmifr.library.common.Ref;
import com.helmifr.library.common.SearchPatterns;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthorService {

    private final AuthorRepository authors;
    private final BookRepository books;

    public PageResponse<AuthorResponse> search(String q, Pageable pageable) {
        return PageResponse.of(authors.search(SearchPatterns.contains(q), pageable).map(AuthorResponse::from));
    }

    public List<Ref> options() {
        return authors.findAllByDeletedFalseOrderByNameAsc().stream()
                .map(a -> new Ref(a.getId(), a.getName()))
                .toList();
    }

    public AuthorResponse get(Long id) {
        return AuthorResponse.from(require(id));
    }

    /** Loads an active author for use by other features; throws 404 if missing or deleted. */
    public Author require(Long id) {
        return authors.findByIdAndDeletedFalse(id).orElseThrow(() -> new NotFoundException("Author", id));
    }

    @Transactional
    public AuthorResponse create(AuthorRequest request) {
        Author author = new Author();
        apply(author, request);
        return AuthorResponse.from(authors.save(author));
    }

    @Transactional
    public AuthorResponse update(Long id, AuthorRequest request) {
        Author author = require(id);
        apply(author, request);
        return AuthorResponse.from(author);
    }

    @Transactional
    public void delete(Long id) {
        Author author = require(id);
        if (books.existsByAuthorIdAndDeletedFalse(id)) {
            throw new ConflictException("Author still has books. Delete or reassign them first.");
        }
        author.setDeleted(true);
    }

    private static void apply(Author author, AuthorRequest request) {
        author.setName(request.name().trim());
        author.setDateOfBirth(request.dateOfBirth());
    }
}
