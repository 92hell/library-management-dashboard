package com.helmifr.library.loan;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    String ACTIVE = "com.helmifr.library.loan.LoanStatus.ACTIVE";
    String PENDING = "com.helmifr.library.loan.LoanStatus.PENDING";
    String WAITLISTED = "com.helmifr.library.loan.LoanStatus.WAITLISTED";
    String READY = "com.helmifr.library.loan.LoanStatus.READY";
    /** Statuses in which a copy is off the shelf: lent, or set aside on the holds shelf. */
    String HOLDS_COPY = "(" + "com.helmifr.library.loan.LoanStatus.ACTIVE, com.helmifr.library.loan.LoanStatus.READY)";
    /** Statuses that count as a member's open request or loan. */
    String OPEN = "(" + "com.helmifr.library.loan.LoanStatus.PENDING, com.helmifr.library.loan.LoanStatus.WAITLISTED, "
            + "com.helmifr.library.loan.LoanStatus.READY, com.helmifr.library.loan.LoanStatus.ACTIVE)";

    @EntityGraph(attributePaths = {"book", "book.author", "member", "copy"})
    Optional<Loan> findByIdAndDeletedFalse(Long id);

    @EntityGraph(attributePaths = {"book", "book.author", "member", "copy"})
    Optional<Loan> findByIdAndMemberIdAndDeletedFalse(Long id, Long memberId);

    @EntityGraph(attributePaths = {"book", "book.author", "member", "copy"})
    List<Loan> findAllByMemberIdAndDeletedFalseOrderByIdDesc(Long memberId);

    /** Is the book lent out right now, ignoring the loan being edited (null when creating)? */
    @Query("SELECT COUNT(l) > 0 FROM Loan l WHERE l.book.id = :bookId AND l.deleted = false AND l.status = " + ACTIVE
            + " AND (:excludeLoanId IS NULL OR l.id <> :excludeLoanId)")
    boolean isBookOnLoan(@Param("bookId") Long bookId, @Param("excludeLoanId") Long excludeLoanId);

    /** [bookId, number of its copies off the shelf (lent or set aside)] for each of the given books. */
    @Query("SELECT l.book.id, COUNT(l) FROM Loan l WHERE l.book.id IN :bookIds AND l.deleted = false AND l.status IN "
            + HOLDS_COPY + " GROUP BY l.book.id")
    List<Object[]> countOnLoanByBook(@Param("bookIds") Collection<Long> bookIds);

    /** Is this copy off the shelf (lent or set aside), ignoring the loan being edited (null when creating)? */
    @Query("SELECT COUNT(l) > 0 FROM Loan l WHERE l.copy.id = :copyId AND l.deleted = false AND l.status IN " + HOLDS_COPY
            + " AND (:excludeLoanId IS NULL OR l.id <> :excludeLoanId)")
    boolean isCopyOnLoan(@Param("copyId") Long copyId, @Param("excludeLoanId") Long excludeLoanId);

    @EntityGraph(attributePaths = {"book", "book.author", "member", "copy"})
    @Query("SELECT l FROM Loan l WHERE l.copy.id = :copyId AND l.deleted = false AND l.status = " + ACTIVE)
    Optional<Loan> findActiveByCopy(@Param("copyId") Long copyId);

    @EntityGraph(attributePaths = {"member"})
    @Query("SELECT l FROM Loan l WHERE l.copy.id IN :copyIds AND l.deleted = false AND l.status IN " + HOLDS_COPY)
    List<Loan> findActiveByCopies(@Param("copyIds") Collection<Long> copyIds);

    /** Pending requests and active loans of a member. */
    @Query("SELECT COUNT(l) FROM Loan l WHERE l.member.id = :memberId AND l.deleted = false AND l.status IN "
            + OPEN)
    long countOpenByMember(@Param("memberId") Long memberId);

    default boolean hasOpenLoans(Long memberId) {
        return countOpenByMember(memberId) > 0;
    }

    @Query("SELECT COUNT(l) > 0 FROM Loan l WHERE l.member.id = :memberId AND l.book.id = :bookId"
            + " AND l.deleted = false AND l.status IN " + OPEN)
    boolean existsOpenForMemberAndBook(@Param("memberId") Long memberId, @Param("bookId") Long bookId);

    /**
     * @param pattern     from {@link com.helmifr.library.common.SearchPatterns}, or null for all
     * @param status      null for all statuses
     * @param overdueOnly only loans due before {@code today}
     */
    @EntityGraph(attributePaths = {"book", "book.author", "member", "copy"})
    @Query(value = """
            SELECT l FROM Loan l JOIN l.book b JOIN b.author a JOIN l.member m LEFT JOIN l.copy cp
            WHERE l.deleted = false
              AND (:pattern IS NULL
                   OR LOWER(m.name) LIKE :pattern ESCAPE '!'
                   OR LOWER(m.libraryCardId) LIKE :pattern ESCAPE '!'
                   OR LOWER(b.title) LIKE :pattern ESCAPE '!'
                   OR LOWER(a.name) LIKE :pattern ESCAPE '!'
                   OR LOWER(cp.barcode) LIKE :pattern ESCAPE '!')
              AND (:borrowDate IS NULL OR l.borrowDate = :borrowDate)
              AND (:status IS NULL OR l.status = :status)
              AND (:overdueOnly = false OR l.dueDate < :today)
            ORDER BY l.id DESC""",
            countQuery = """
            SELECT COUNT(l) FROM Loan l JOIN l.book b JOIN b.author a JOIN l.member m LEFT JOIN l.copy cp
            WHERE l.deleted = false
              AND (:pattern IS NULL
                   OR LOWER(m.name) LIKE :pattern ESCAPE '!'
                   OR LOWER(m.libraryCardId) LIKE :pattern ESCAPE '!'
                   OR LOWER(b.title) LIKE :pattern ESCAPE '!'
                   OR LOWER(a.name) LIKE :pattern ESCAPE '!'
                   OR LOWER(cp.barcode) LIKE :pattern ESCAPE '!')
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

    // ---- Holds queue ---------------------------------------------------------------------------

    /** Is anyone waiting for this title (a request not yet decided, or in the queue)? */
    @Query("SELECT COUNT(l) > 0 FROM Loan l WHERE l.book.id = :bookId AND l.deleted = false AND l.status IN ("
            + PENDING + ", " + WAITLISTED + ")")
    boolean anyoneWaitingFor(@Param("bookId") Long bookId);

    @Query("SELECT COUNT(l) FROM Loan l WHERE l.book.id = :bookId AND l.deleted = false AND l.status IN ("
            + PENDING + ", " + WAITLISTED + ")")
    long countWaitingFor(@Param("bookId") Long bookId);

    /** Any request, hold or loan of the title still open (PENDING, WAITLISTED, READY or ACTIVE)? */
    @Query("SELECT COUNT(l) > 0 FROM Loan l WHERE l.book.id = :bookId AND l.deleted = false AND l.status IN " + OPEN)
    boolean anyOpenForBook(@Param("bookId") Long bookId);

    /** The queue for a title, first in line first. */
    @EntityGraph(attributePaths = {"book", "member"})
    @Query("SELECT l FROM Loan l WHERE l.book.id = :bookId AND l.deleted = false AND l.status = " + WAITLISTED
            + " ORDER BY l.requestedAt ASC, l.id ASC")
    List<Loan> findQueue(@Param("bookId") Long bookId);

    @EntityGraph(attributePaths = {"book", "member", "copy"})
    @Query("SELECT l FROM Loan l WHERE l.deleted = false AND l.status = " + READY + " AND l.readyUntil < :today")
    List<Loan> findUncollectedHolds(@Param("today") LocalDate today);

    @Query("SELECT COUNT(l) FROM Loan l WHERE l.deleted = false AND l.status = " + ACTIVE + " AND l.dueDate < :today")
    long countOverdue(@Param("today") LocalDate today);

    @Query("SELECT COUNT(l) FROM Loan l WHERE l.deleted = false AND l.status = " + ACTIVE + " AND l.dueDate = :day")
    long countDueOn(@Param("day") LocalDate day);
}
