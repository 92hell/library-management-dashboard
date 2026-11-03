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

    /**
     * The catalogue, filtered; the order comes from the Pageable's Sort (see {@link BookOrder}).
     *
     * @param pattern       from {@link com.helmifr.library.common.SearchPatterns}, or null for all
     * @param category      "%,Category,%" to match a whole category name, or null
     * @param availableOnly only titles with a copy on the shelf (not lent, not set aside)
     */
    @EntityGraph(attributePaths = "author")
    @Query(value = """
            SELECT b FROM Book b JOIN b.author a
            WHERE b.deleted = false
              AND (:pattern IS NULL
                   OR LOWER(b.title) LIKE :pattern ESCAPE '!'
                   OR LOWER(b.publisherName) LIKE :pattern ESCAPE '!'
                   OR LOWER(b.categories) LIKE :pattern ESCAPE '!'
                   OR LOWER(a.name) LIKE :pattern ESCAPE '!'
                   OR b.isbn LIKE :pattern ESCAPE '!')
              AND (:category IS NULL OR CONCAT(',', REPLACE(b.categories, ', ', ','), ',') LIKE :category)
              AND (:authorId IS NULL OR a.id = :authorId)
              AND (:availableOnly = false OR EXISTS (
                   SELECT c FROM Copy c WHERE c.book = b AND c.deleted = false AND NOT EXISTS (
                       SELECT l FROM Loan l WHERE l.copy = c AND l.deleted = false
                         AND l.status IN (com.helmifr.library.loan.LoanStatus.READY,
                                          com.helmifr.library.loan.LoanStatus.ACTIVE))))""",
            countQuery = """
            SELECT COUNT(b) FROM Book b JOIN b.author a
            WHERE b.deleted = false
              AND (:pattern IS NULL
                   OR LOWER(b.title) LIKE :pattern ESCAPE '!'
                   OR LOWER(b.publisherName) LIKE :pattern ESCAPE '!'
                   OR LOWER(b.categories) LIKE :pattern ESCAPE '!'
                   OR LOWER(a.name) LIKE :pattern ESCAPE '!'
                   OR b.isbn LIKE :pattern ESCAPE '!')
              AND (:category IS NULL OR CONCAT(',', REPLACE(b.categories, ', ', ','), ',') LIKE :category)
              AND (:authorId IS NULL OR a.id = :authorId)
              AND (:availableOnly = false OR EXISTS (
                   SELECT c FROM Copy c WHERE c.book = b AND c.deleted = false AND NOT EXISTS (
                       SELECT l FROM Loan l WHERE l.copy = c AND l.deleted = false
                         AND l.status IN (com.helmifr.library.loan.LoanStatus.READY,
                                          com.helmifr.library.loan.LoanStatus.ACTIVE))))""")
    Page<Book> search(@Param("pattern") String pattern, @Param("category") String category,
                      @Param("authorId") Long authorId, @Param("availableOnly") boolean availableOnly,
                      Pageable pageable);

    @Query("SELECT DISTINCT b.categories FROM Book b WHERE b.deleted = false AND b.categories IS NOT NULL")
    List<String> findAllCategoryStrings();

    boolean existsByIsbnAndDeletedFalseAndIdNot(String isbn, Long id);

    boolean existsByIsbnAndDeletedFalse(String isbn);
}
