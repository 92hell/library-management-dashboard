package com.helmifr.library.loan;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static java.util.Comparator.reverseOrder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;

/**
 * The order of a member's own loan list: what needs their attention first, then their history.
 * <ol>
 *   <li>holds ready for pickup, collect-by date soonest first</li>
 *   <li>overdue loans, then books on loan, due date soonest first</li>
 *   <li>requests and places in the queue, in the order they were made</li>
 *   <li>returned, cancelled, declined and expired loans, newest first by return date, else request date</li>
 * </ol>
 */
final class MyLoanOrder {

    private MyLoanOrder() {}

    static Comparator<LoanResponse> attentionFirst(ZoneId zone) {
        Comparator<LoanResponse> olderRecordFirst = comparing(LoanResponse::id);
        Comparator<LoanResponse> ready = comparing(LoanResponse::readyUntil, nullsLast(naturalOrder()));
        Comparator<LoanResponse> due = comparing(LoanResponse::dueDate, nullsLast(naturalOrder()));
        Comparator<LoanResponse> requested = comparing(LoanResponse::requestedAt, nullsLast(naturalOrder()));
        Comparator<LoanResponse> newestFirst = comparing((LoanResponse l) -> historyDate(l, zone), nullsLast(reverseOrder()))
                .thenComparing(olderRecordFirst.reversed());
        return comparing(MyLoanOrder::group).thenComparing((a, b) -> switch (group(a)) {
            case READY -> ready.thenComparing(olderRecordFirst).compare(a, b);
            case OVERDUE, ON_LOAN -> due.thenComparing(olderRecordFirst).compare(a, b);
            case REQUESTED -> requested.thenComparing(olderRecordFirst).compare(a, b);
            case HISTORY -> newestFirst.compare(a, b);
        });
    }

    /** In list order. */
    private enum Group { READY, OVERDUE, ON_LOAN, REQUESTED, HISTORY }

    private static Group group(LoanResponse loan) {
        return switch (loan.status()) {
            case READY -> Group.READY;
            case ACTIVE -> loan.overdue() ? Group.OVERDUE : Group.ON_LOAN;
            case PENDING, WAITLISTED -> Group.REQUESTED;
            case RETURNED, REJECTED, CANCELLED, EXPIRED -> Group.HISTORY;
        };
    }

    private static LocalDate historyDate(LoanResponse loan, ZoneId zone) {
        if (loan.returnDate() != null) {
            return loan.returnDate();
        }
        if (loan.requestedAt() != null) {
            return LocalDate.ofInstant(loan.requestedAt(), zone);
        }
        return loan.borrowDate();
    }
}
