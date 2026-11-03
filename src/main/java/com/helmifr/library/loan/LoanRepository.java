package com.helmifr.library.loan;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    String ACTIVE = "com.helmifr.library.loan.LoanStatus.ACTIVE";
    String PENDING = "com.helmifr.library.loan.LoanStatus.PENDING";

    @EntityGraph(attributePaths = {"book", "book.author", "member"})
    Optional<Loan> findByIdAndDeletedFalse(Long id);

    @EntityGraph(attributePaths = {"book", "book.author", "member"})
    Optional<Loan> findByIdAndMemberIdAndDeletedFalse(Long id, Long memberId);

    @EntityGraph(attributePaths = {"book", "book.author", "member"})
    List<Loan> findAllByMemberIdAndDeletedFalseOrderByIdDesc(Long memberId);

    /** Is the book lent out right now, ignoring the loan being edited (null when creating)? */
    @Query("SELECT COUNT(l) > 0 FROM Loan l WHERE l.book.id = :bookId AND l.deleted = false AND l.status = " + ACTIVE
            + " AND (:excludeLoanId IS NULL OR l.id <> :excludeLoanId)")
    boolean isBookOnLoan(@Param("bookId") Long bookId, @Param("excludeLoanId") Long excludeLoanId);

    @Query("SELECT l.book.id FROM Loan l WHERE l.book.id IN :bookIds AND l.deleted = false AND l.status = " + ACTIVE)
    Set<Long> findBookIdsOnLoan(@Param("bookIds") Collection<Long> bookIds);

    /** Pending requests and active loans of a member. */
    @Query("SELECT COUNT(l) FROM Loan l WHERE l.member.id = :memberId AND l.deleted = false AND l.status IN ("
            + PENDING + ", " + ACTIVE + ")")
    long countOpenByMember(@Param("memberId") Long memberId);

    default boolean hasOpenLoans(Long memberId) {
        return countOpenByMember(memberId) > 0;
    }

    @Query("SELECT COUNT(l) > 0 FROM Loan l WHERE l.member.id = :memberId AND l.book.id = :bookId"
            + " AND l.deleted = false AND l.status IN (" + PENDING + ", " + ACTIVE + ")")
    boolean existsOpenForMemberAndBook(@Param("memberId") Long memberId, @Param("bookId") Long bookId);

    /**
     * @param pattern     from {@link com.helmifr.library.common.SearchPatterns}, or null for all
     * @param status      null for all statuses
     * @param overdueOnly only loans due before {@code today}
     */
    @EntityGraph(attributePaths = {"book", "book.author", "member"})
    @Query(value = """
            SELECT l FROM Loan l JOIN l.book b JOIN b.author a JOIN l.member m
            WHERE l.deleted = false
              AND (:pattern IS NULL
                   OR LOWER(m.name) LIKE :pattern ESCAPE '!'
                   OR LOWER(m.memberCode) LIKE :pattern ESCAPE '!'
                   OR LOWER(b.title) LIKE :pattern ESCAPE '!'
                   OR LOWER(a.name) LIKE :pattern ESCAPE '!')
              AND (:borrowDate IS NULL OR l.borrowDate = :borrowDate)
              AND (:status IS NULL OR l.status = :status)
              AND (:overdueOnly = false OR l.dueDate < :today)
            ORDER BY l.id DESC""",
            countQuery = """
            SELECT COUNT(l) FROM Loan l JOIN l.book b JOIN b.author a JOIN l.member m
            WHERE l.deleted = false
              AND (:pattern IS NULL
                   OR LOWER(m.name) LIKE :pattern ESCAPE '!'
                   OR LOWER(m.memberCode) LIKE :pattern ESCAPE '!'
                   OR LOWER(b.title) LIKE :pattern ESCAPE '!'
                   OR LOWER(a.name) LIKE :pattern ESCAPE '!')
              AND (:borrowDate IS NULL OR l.borrowDate = :borrowDate)
              AND (:status IS NULL OR l.status = :status)
              AND (:overdueOnly = false OR l.dueDate < :today)""")
    Page<Loan> search(@Param("pattern") String pattern,
                      @Param("borrowDate") LocalDate borrowDate,
                      @Param("status") LoanStatus status,
                      @Param("overdueOnly") boolean overdueOnly,
                      @Param("today") LocalDate today,
                      Pageable pageable);

    // ---- Reminders and dashboard ---------------------------------------------------------------

    @EntityGraph(attributePaths = {"book", "member"})
    @Query("SELECT l FROM Loan l WHERE l.deleted = false AND l.status = " + ACTIVE
            + " AND l.dueSoonNotified = false AND l.dueDate >= :today AND l.dueDate <= :until")
    List<Loan> findDueSoonNotYetNotified(@Param("today") LocalDate today, @Param("until") LocalDate until);

    @EntityGraph(attributePaths = {"book", "member"})
    @Query("SELECT l FROM Loan l WHERE l.deleted = false AND l.status = " + ACTIVE
            + " AND l.overdueNotified = false AND l.dueDate < :today")
    List<Loan> findOverdueNotYetNotified(@Param("today") LocalDate today);

    long countByStatusAndDeletedFalse(LoanStatus status);

    @Query("SELECT COUNT(l) FROM Loan l WHERE l.deleted = false AND l.status = " + ACTIVE + " AND l.dueDate < :today")
    long countOverdue(@Param("today") LocalDate today);

    @Query("SELECT COUNT(l) FROM Loan l WHERE l.deleted = false AND l.status = " + ACTIVE + " AND l.dueDate = :day")
    long countDueOn(@Param("day") LocalDate day);
}
