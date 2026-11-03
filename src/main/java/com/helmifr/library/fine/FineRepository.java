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

    @EntityGraph(attributePaths = {"loan", "loan.book", "member"})
    List<Fine> findAllByMemberIdOrderByIdDesc(Long memberId);

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM Fine f WHERE f.member.id = :memberId AND f.status = " + UNPAID)
    long sumUnpaidByMember(@Param("memberId") Long memberId);

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM Fine f WHERE f.status = " + UNPAID)
    long sumUnpaid();

    @EntityGraph(attributePaths = {"loan", "loan.book", "member"})
    @Query(value = """
            SELECT f FROM Fine f JOIN f.member m JOIN f.loan l JOIN l.book b
            WHERE (:status IS NULL OR f.status = :status)
              AND (:pattern IS NULL
                   OR LOWER(m.name) LIKE :pattern ESCAPE '!'
                   OR LOWER(m.libraryCardId) LIKE :pattern ESCAPE '!'
                   OR LOWER(b.title) LIKE :pattern ESCAPE '!')
            ORDER BY f.id DESC""",
            countQuery = """
            SELECT COUNT(f) FROM Fine f JOIN f.member m JOIN f.loan l JOIN l.book b
            WHERE (:status IS NULL OR f.status = :status)
              AND (:pattern IS NULL
                   OR LOWER(m.name) LIKE :pattern ESCAPE '!'
                   OR LOWER(m.libraryCardId) LIKE :pattern ESCAPE '!'
                   OR LOWER(b.title) LIKE :pattern ESCAPE '!')""")
    Page<Fine> search(@Param("pattern") String pattern, @Param("status") FineStatus status, Pageable pageable);
}
