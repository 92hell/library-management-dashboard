package com.helmifr.library.report;

import com.helmifr.library.loan.Loan;
import com.helmifr.library.report.ReportDtos.MemberCount;
import com.helmifr.library.report.ReportDtos.TitleCount;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /** Books lent on each day of the range that had any. */
    Map<LocalDate, Long> loansPerDay(ReportRange r) {
        return perDay("SELECT l.borrowDate, COUNT(l) FROM Loan l WHERE " + BORROWED_IN + " GROUP BY l.borrowDate", r);
    }

    /** Books returned on each day of the range that had any; only those returned after their due date if {@code lateOnly}. */
    Map<LocalDate, Long> returnsPerDay(ReportRange r, boolean lateOnly) {
        return perDay("SELECT l.returnDate, COUNT(l) FROM Loan l WHERE " + LENT
                + " AND l.status = com.helmifr.library.loan.LoanStatus.RETURNED AND l.returnDate BETWEEN :from AND :to"
                + (lateOnly ? " AND l.returnDate > l.dueDate" : "") + " GROUP BY l.returnDate", r);
    }

    private Map<LocalDate, Long> perDay(String jpql, ReportRange r) {
        Map<LocalDate, Long> counts = new HashMap<>();
        for (Object[] row : em.createQuery(jpql, Object[].class)
                .setParameter("from", r.from()).setParameter("to", r.to()).getResultList()) {
            counts.put((LocalDate) row[0], (Long) row[1]);
        }
        return counts;
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
                        + " WHERE f.loan.deleted = false AND f.createdAt >= :from AND f.createdAt < :to", Long.class)
                .setParameter("from", from).setParameter("to", toExclusive)
                .getSingleResult();
    }

    long finesCollected(Instant from, Instant toExclusive) {
        // Money actually taken in the period: every payment, including part payments.
        return em.createQuery("SELECT COALESCE(SUM(p.amount), 0) FROM FinePayment p"
                        + " WHERE p.fine.loan.deleted = false AND p.paidAt >= :from AND p.paidAt < :to", Long.class)
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

    /** @param limit at most this many rows (the caller asks for one more to detect "too many") */
    List<Loan> loansIn(ReportRange r, int limit) {
        return em.createQuery("SELECT l FROM Loan l JOIN FETCH l.book b JOIN FETCH b.author JOIN FETCH l.member"
                        + " LEFT JOIN FETCH l.copy WHERE " + BORROWED_IN + " ORDER BY l.borrowDate ASC, l.id ASC", Loan.class)
                .setParameter("from", r.from()).setParameter("to", r.to())
                .setMaxResults(limit)
                .getResultList();
    }

    private long count(String jpql, ReportRange r) {
        return em.createQuery(jpql, Long.class).setParameter("from", r.from()).setParameter("to", r.to()).getSingleResult();
    }
}
