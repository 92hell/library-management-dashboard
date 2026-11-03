package com.helmifr.library.author;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    Optional<Author> findByIdAndDeletedFalse(Long id);

    List<Author> findAllByDeletedFalseOrderByNameAsc();

    Optional<Author> findFirstByNameIgnoreCaseAndDeletedFalse(String name);

    /** @param pattern from {@link com.helmifr.library.common.SearchPatterns}, or null for all */
    @Query("""
            SELECT a FROM Author a
            WHERE a.deleted = false
              AND (:pattern IS NULL OR LOWER(a.name) LIKE :pattern ESCAPE '!')
            ORDER BY a.createdAt DESC, a.id DESC""")
    Page<Author> search(@Param("pattern") String pattern, Pageable pageable);
}
