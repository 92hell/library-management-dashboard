package com.helmifr.library.report;

import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() {}

    public record TitleCount(Long bookId, String title, String authorName, long loans) {}

    public record MemberCount(Long memberId, String name, String libraryCardId, long loans) {}

    /**
     * @param loans          books lent in the range (by borrow date)
     * @param returns        books returned in the range
     * @param dueInPeriod    loans that fell due in the range, up to yesterday
     * @param lateInPeriod   of those, returned late or still out
     * @param lateRate       lateInPeriod / dueInPeriod (3 decimals); null when nothing fell due
     * @param activeMembers  members who borrowed at least once in the range
     * @param newMembers     members added in the range
     * @param finesCharged   late fees charged in the range
     * @param finesCollected late fees paid in the range
     */
    public record Summary(LocalDate from, LocalDate to, long loans, long returns, long dueInPeriod, long lateInPeriod,
                          Double lateRate, long activeMembers, long newMembers, long finesCharged, long finesCollected,
                          String currency, List<TitleCount> mostBorrowed, List<MemberCount> mostActiveMembers) {}
}
