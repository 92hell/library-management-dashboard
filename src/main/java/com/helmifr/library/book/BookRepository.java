package com.helmifr.library.book;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {

    @EntityGraph(attributePaths = "author")
    Optional<Book> findByIdAndDeletedFalse(Long id);

    /** Locks the book row so two concurrent loans of the same book cannot both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Book b WHERE b.id = :id AND b.deleted = false")
    Optional<Book> findActiveForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "author")
    List<Book> findAllByAuthorIdAndDeletedFalseOrderByTitleAsc(Long authorId);

    @EntityGraph(attributePaths = "author")
    List<Book> findAllByDeletedFalseOrderByTitleAsc();

    boolean existsByAuthorIdAndDeletedFalse(Long authorId);

    long countByDeletedFalse();

    /** @param pattern from {@link com.helmifr.library.common.SearchPatterns}, or null for all */
    @EntityGraph(attributePaths = "author")
    @Query(value = """
            SELECT b FROM Book b JOIN b.author a
            WHERE b.deleted = false
              AND (:pattern IS NULL
                   OR LOWER(b.title) LIKE :pattern ESCAPE '!'
                   OR LOWER(b.publisherName) LIKE :pattern ESCAPE '!'
                   OR LOWER(b.categories) LIKE :pattern ESCAPE '!'
                   OR LOWER(a.name) LIKE :pattern ESCAPE '!')
            ORDER BY b.createdAt DESC, b.id DESC""",
            countQuery = """
            SELECT COUNT(b) FROM Book b JOIN b.author a
            WHERE b.deleted = false
              AND (:pattern IS NULL
                   OR LOWER(b.title) LIKE :pattern ESCAPE '!'
                   OR LOWER(b.publisherName) LIKE :pattern ESCAPE '!'
                   OR LOWER(b.categories) LIKE :pattern ESCAPE '!'
                   OR LOWER(a.name) LIKE :pattern ESCAPE '!')""")
    Page<Book> search(@Param("pattern") String pattern, Pageable pageable);
}
