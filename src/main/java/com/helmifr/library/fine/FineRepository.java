package com.helmifr.library.fine;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FineRepository extends JpaRepository<Fine, Long> {

    String UNPAID = "com.helmifr.library.fine.FineStatus.UNPAID";

    Optional<Fine> findByLoanId(Long loanId);

    /** A member's fines; fines of deleted loans no longer count anywhere. */
    @EntityGraph(attributePaths = {"loan", "loan.book", "member"})
    List<Fine> findAllByMemberIdAndLoanDeletedFalseOrderByIdDesc(Long memberId);

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM Fine f WHERE f.member.id = :memberId AND f.loan.deleted = false"
            + " AND f.status = " + UNPAID)
    long sumUnpaidByMember(@Param("memberId") Long memberId);

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM Fine f WHERE f.loan.deleted = false AND f.status = " + UNPAID)
    long sumUnpaid();

    /** Locks the fine row, so paying and waiving the same fine cannot both succeed. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Fine f WHERE f.id = :id")
    Optional<Fine> findForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"loan", "loan.book", "member"})
    @Query(value = """
            SELECT f FROM Fine f JOIN f.member m JOIN f.loan l JOIN l.book b
            WHERE l.deleted = false AND (:status IS NULL OR f.status = :status)
              AND (:pattern IS NULL
                   OR LOWER(m.name) LIKE :pattern ESCAPE '!'
                   OR LOWER(m.libraryCardId) LIKE :pattern ESCAPE '!'
                   OR LOWER(b.title) LIKE :pattern ESCAPE '!')
            ORDER BY f.id DESC""",
            countQuery = """
            SELECT COUNT(f) FROM Fine f JOIN f.member m JOIN f.loan l JOIN l.book b
            WHERE l.deleted = false AND (:status IS NULL OR f.status = :status)
              AND (:pattern IS NULL
                   OR LOWER(m.name) LIKE :pattern ESCAPE '!'
                   OR LOWER(m.libraryCardId) LIKE :pattern ESCAPE '!'
                   OR LOWER(b.title) LIKE :pattern ESCAPE '!')""")
    Page<Fine> search(@Param("pattern") String pattern, @Param("status") FineStatus status, Pageable pageable);
}
