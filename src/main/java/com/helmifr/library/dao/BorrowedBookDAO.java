// com.helmifr.library.dao.BorrowedBookDAO.java
package com.helmifr.library.dao;

import com.helmifr.library.entity.BorrowedBook;
import com.helmifr.library.entity.view.BorrowedBookListView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

import java.util.List;

public interface BorrowedBookDAO extends JpaRepository<BorrowedBook, Long> {

    @Query("SELECT NEW com.helmifr.library.entity.view.BorrowedBookListView(" +
            "b.id, b.book.title, b.book.author.name, b.member.name, b.borrowDate, b.returnDate) " +
            "FROM BorrowedBook b " +
            "WHERE b.isDeleted = false " +
            "ORDER BY b.borrowDate DESC, b.id DESC")
    List<BorrowedBookListView> findAllBorrowedBooksListView();

    @Query("SELECT NEW com.helmifr.library.entity.view.BorrowedBookListView(" +
            "b.id, b.book.title, b.book.author.name, b.member.name, b.borrowDate, b.returnDate) " +
            "FROM BorrowedBook b JOIN b.book bk JOIN bk.author a JOIN b.member m " +
            "WHERE b.isDeleted = false AND (" +
            "  (:generalSearchTerm IS NULL OR " +
            "   LOWER(m.name) LIKE LOWER(CONCAT('%', :generalSearchTerm, '%')) OR " +
            "   LOWER(bk.title) LIKE LOWER(CONCAT('%', :generalSearchTerm, '%')) OR " +
            "   LOWER(a.name) LIKE LOWER(CONCAT('%', :generalSearchTerm, '%'))" +
            "  ) AND " +
            "  (:borrowDateFilter IS NULL OR b.borrowDate = :borrowDateFilter)" +
            ") ORDER BY b.borrowDate DESC, b.id DESC")
    List<BorrowedBookListView> searchBorrowedBooksListViewByCriteria(
            @Param("generalSearchTerm") String generalSearchTerm,
            @Param("borrowDateFilter") LocalDate borrowDateFilter);
}