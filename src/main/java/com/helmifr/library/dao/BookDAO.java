package com.helmifr.library.dao;

import com.helmifr.library.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookDAO extends JpaRepository<Book, Long> {
    List<Book> findAllByIsDeletedFalse();
    List<Book> findAllByAuthorIdAndIsDeletedFalse(long authorId);

    @Query("SELECT b FROM Book b JOIN b.author a WHERE b.isDeleted = false AND (" +
            "  (:searchTerm IS NULL) OR " +
            "  (LOWER(b.title) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) OR " +
            "  (LOWER(b.publisherName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) OR " +
            "  (LOWER(b.categories) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) OR " +
            "  (LOWER(a.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')))" +
            ") ORDER BY b.createdAt DESC")
    List<Book> searchBooksByCriteria(@Param("searchTerm") String searchTerm);
}
