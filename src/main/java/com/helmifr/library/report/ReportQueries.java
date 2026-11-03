package com.helmifr.library.report;

import com.helmifr.library.loan.Loan;
import com.helmifr.library.report.ReportDtos.MemberCount;
import com.helmifr.library.report.ReportDtos.TitleCount;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Read-only aggregate queries for reports. A "loan" is a book actually lent: ACTIVE or RETURNED, not deleted. */
@Repository
@RequiredArgsConstructor
class ReportQueries {

    private static final String LENT = "l.deleted = false AND l.status IN ("
            + "com.helmifr.library.loan.LoanStatus.ACTIVE, com.helmifr.library.loan.LoanStatus.RETURNED)";
    private static final String BORROWED_IN = LENT + " AND l.borrowDate BETWEEN :from AND :to";

    private final EntityManager em;

    long loans(ReportRange r) {
        return count("SELECT COUNT(l) FROM Loan l WHERE " + BORROWED_IN, r);
    }

    long returns(ReportRange r) {
        return count("SELECT COUNT(l) FROM Loan l WHERE " + LENT
                + " AND l.status = com.helmifr.library.loan.LoanStatus.RETURNED AND l.returnDate BETWEEN :from AND :to", r);
    }

    /** Loans due in [from, min(to, yesterday)]. */
    long dueInPeriod(ReportRange r, LocalDate today) {
        return em.createQuery("SELECT COUNT(l) FROM Loan l WHERE " + LENT
                        + " AND l.dueDate BETWEEN :from AND :to AND l.dueDate < :today", Long.class)
                .setParameter("from", r.from()).setParameter("to", r.to()).setParameter("today", today)
                .getSingleResult();
    }

    /** Of the loans due in the period: returned after the due date, or still out. */
    long lateInPeriod(ReportRange r, LocalDate today) {
        return em.createQuery("SELECT COUNT(l) FROM Loan l WHERE " + LENT
                        + " AND l.dueDate BETWEEN :from AND :to AND l.dueDate < :today"
                        + " AND (l.status = com.helmifr.library.loan.LoanStatus.ACTIVE OR l.returnDate > l.dueDate)", Long.class)
                .setParameter("from", r.from()).setParameter("to", r.to()).setParameter("today", today)
                .getSingleResult();
    }

    long activeMembers(ReportRange r) {
        return count("SELECT COUNT(DISTINCT l.member.id) FROM Loan l WHERE " + BORROWED_IN, r);
    }

    long newMembers(Instant from, Instant toExclusive) {
        return em.createQuery("SELECT COUNT(m) FROM Member m WHERE m.deleted = false"
                        + " AND m.createdAt >= :from AND m.createdAt < :to", Long.class)
                .setParameter("from", from).setParameter("to", toExclusive)
                .getSingleResult();
    }

    long finesCharged(Instant from, Instant toExclusive) {
        return em.createQuery("SELECT COALESCE(SUM(f.amount), 0) FROM Fine f"
                        + " WHERE f.createdAt >= :from AND f.createdAt < :to", Long.class)
                .setParameter("from", from).setParameter("to", toExclusive)
                .getSingleResult();
    }

    long finesCollected(Instant from, Instant toExclusive) {
        return em.createQuery("SELECT COALESCE(SUM(f.amount), 0) FROM Fine f"
                        + " WHERE f.status = com.helmifr.library.fine.FineStatus.PAID"
                        + " AND f.settledAt >= :from AND f.settledAt < :to", Long.class)
                .setParameter("from", from).setParameter("to", toExclusive)
                .getSingleResult();
    }

    List<TitleCount> mostBorrowed(ReportRange r, int limit) {
        return em.createQuery("SELECT new com.helmifr.library.report.ReportDtos$TitleCount("
                        + "b.id, b.title, a.name, COUNT(l)) FROM Loan l JOIN l.book b JOIN b.author a WHERE " + BORROWED_IN
                        + " GROUP BY b.id, b.title, a.name ORDER BY COUNT(l) DESC, b.title ASC", TitleCount.class)
                .setParameter("from", r.from()).setParameter("to", r.to())
                .setMaxResults(limit)
                .getResultList();
    }

    List<MemberCount> mostActiveMembers(ReportRange r, int limit) {
        return em.createQuery("SELECT new com.helmifr.library.report.ReportDtos$MemberCount("
                        + "m.id, m.name, m.libraryCardId, COUNT(l)) FROM Loan l JOIN l.member m WHERE " + BORROWED_IN
                        + " GROUP BY m.id, m.name, m.libraryCardId ORDER BY COUNT(l) DESC, m.name ASC", MemberCount.class)
                .setParameter("from", r.from()).setParameter("to", r.to())
                .setMaxResults(limit)
                .getResultList();
    }

    List<Loan> loansIn(ReportRange r) {
        return em.createQuery("SELECT l FROM Loan l JOIN FETCH l.book b JOIN FETCH b.author JOIN FETCH l.member"
                        + " LEFT JOIN FETCH l.copy WHERE " + BORROWED_IN + " ORDER BY l.borrowDate ASC, l.id ASC", Loan.class)
                .setParameter("from", r.from()).setParameter("to", r.to())
                .getResultList();
    }

    private long count(String jpql, ReportRange r) {
        return em.createQuery(jpql, Long.class).setParameter("from", r.from()).setParameter("to", r.to()).getSingleResult();
    }
}
