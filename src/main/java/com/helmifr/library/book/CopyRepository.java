package com.helmifr.library.book;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CopyRepository extends JpaRepository<Copy, Long> {

    Optional<Copy> findByIdAndDeletedFalse(Long id);

    @EntityGraph(attributePaths = {"book", "book.author"})
    Optional<Copy> findByBarcodeAndDeletedFalse(String barcode);

    boolean existsByBarcode(String barcode);

    /** Any copy with this barcode, withdrawn or not (barcodes are never reused). */
    Optional<Copy> findByBarcode(String barcode);

    List<Copy> findAllByBookIdAndDeletedFalseOrderByIdAsc(Long bookId);

    /** [bookId, number of copies in the collection] for each of the given books. */
    @Query("SELECT DISTINCT c.shelfLocation FROM Copy c WHERE c.book.id = :bookId AND c.deleted = false"
            + " AND c.shelfLocation IS NOT NULL ORDER BY c.shelfLocation")
    List<String> findShelfLocations(@Param("bookId") Long bookId);

    @Query("SELECT c.book.id, COUNT(c) FROM Copy c WHERE c.book.id IN :bookIds AND c.deleted = false GROUP BY c.book.id")
    List<Object[]> countByBook(@Param("bookIds") Collection<Long> bookIds);

    @Modifying
    @Query("UPDATE Copy c SET c.deleted = true WHERE c.book.id = :bookId")
    int withdrawAllOfBook(@Param("bookId") Long bookId);
}
