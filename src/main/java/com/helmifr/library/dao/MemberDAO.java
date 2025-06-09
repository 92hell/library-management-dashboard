package com.helmifr.library.dao;

import com.helmifr.library.entity.Book;
import com.helmifr.library.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberDAO extends JpaRepository<Member, Long> {
    List<Member> findAllByIsDeletedFalse();

    @Query("SELECT m FROM Member m WHERE m.isDeleted = false AND (" +
            "  (:searchTerm IS NULL) OR " +
            "  (LOWER(m.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) OR " +
            "  (LOWER(m.email) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) OR " +
            "  (LOWER(m.phone) LIKE LOWER(CONCAT('%', :searchTerm, '%')))" +
            ") ORDER BY m.createdAt DESC")
    List<Member> searchMembersByCriteria(@Param("searchTerm") String searchTerm);

    List<Member> findAllByIsDeletedFalseOrderByCreatedAtDesc();

    @Query("SELECT b FROM Member m JOIN m.borrowedBooks b WHERE m.id = :memberId AND b.isDeleted = false")
    List<Book> findNonDeletedBorrowedBooksByMemberId(@Param("memberId") Long memberId);

    Optional<Member> findByIdAndIsDeletedFalse(Long id);
}
