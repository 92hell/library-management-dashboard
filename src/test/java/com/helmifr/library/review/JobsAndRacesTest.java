package com.helmifr.library.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.helmifr.library.book.Book;
import com.helmifr.library.book.CopyRepository;
import com.helmifr.library.fine.Fine;
import com.helmifr.library.fine.FineRepository;
import com.helmifr.library.fine.FineService;
import com.helmifr.library.fine.FineStatus;
import com.helmifr.library.loan.Loan;
import com.helmifr.library.loan.LoanEventRepository;
import com.helmifr.library.loan.LoanEventType;
import com.helmifr.library.loan.LoanReminderJob;
import com.helmifr.library.loan.LoanRepository;
import com.helmifr.library.loan.LoanService;
import com.helmifr.library.loan.LoanStatus;
import com.helmifr.library.member.Member;
import com.helmifr.library.support.TestData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The scheduled job and concurrent requests, run the way production runs them: without a test
 * transaction around them, on a database of their own (H2, or MariaDB with -Pmariadb, where
 * REPEATABLE READ snapshots made the races visible).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestData.class)
@TestPropertySource(properties = "spring.datasource.url=${test.isolated-db.prefix}races${test.isolated-db.suffix}")
@DisplayName("Scheduled job and concurrent requests (no test transaction)")
class JobsAndRacesTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);

    @Autowired TestData data;
    @Autowired LoanReminderJob job;
    @Autowired LoanService loanService;
    @Autowired FineService fineService;
    @Autowired LoanRepository loans;
    @Autowired LoanEventRepository events;
    @Autowired FineRepository fines;
    @Autowired CopyRepository copies;
    @Autowired TransactionTemplate tx;
    @Autowired com.helmifr.library.member.MemberMerge memberMerge;
    @Autowired com.helmifr.library.loan.MyLoanService myLoans;

    @AfterAll
    static void stop() {
        POOL.shutdownNow();
    }

    private Loan readyHold(Book book, Member member, LocalDate until) {
        return tx.execute(s -> {
            Loan l = data.loan(book, member, LoanStatus.READY, null, null, null);
            l.setCopy(copies.findAllByBookIdAndDeletedFalseOrderByIdAsc(book.getId()).get(0));
            l.setReadyUntil(until);
            l.setRequestedAt(java.time.Instant.now().minusSeconds(3600));
            return loans.save(l);
        });
    }

    @Test
    @DisplayName("the nightly run expires an uncollected hold once, saves it, and passes the copy on")
    void scheduledExpiry() {
        Book book = data.book("Expiry " + System.nanoTime());
        Member late = data.member("Late Collector");
        Loan hold = readyHold(book, late, TODAY.minusDays(1));
        Loan next = tx.execute(s -> {
            Loan l = data.pendingRequest(book, data.member("Next In Line"));
            l.setStatus(LoanStatus.WAITLISTED);
            return loans.save(l);
        });

        job.run();
        job.run();

        assertThat(loans.findById(hold.getId()).orElseThrow().getStatus()).isEqualTo(LoanStatus.EXPIRED);
        assertThat(events.findAllByLoanIdOrderByIdAsc(hold.getId()))
                .filteredOn(e -> e.getType() == LoanEventType.EXPIRED).hasSize(1);
        assertThat(loans.findById(next.getId()).orElseThrow().getStatus()).isEqualTo(LoanStatus.READY);
    }

    @Test
    @DisplayName("the nightly run records that a due-soon reminder was sent, so it is sent once")
    void scheduledReminders() {
        Loan loan = data.activeLoan(data.book("Due " + System.nanoTime()), data.member("Due Tomorrow"),
                TODAY.minusDays(13), TODAY.plusDays(1));

        job.run();

        assertThat(loans.findById(loan.getId()).orElseThrow().isDueSoonNotified()).isTrue();
    }

    @Test
    @DisplayName("two librarians approving two requests for the last copy at once: one is set aside, one queued")
    void concurrentApprovals() throws Exception {
        Book book = data.book("Last Copy " + System.nanoTime());
        Loan a = data.pendingRequest(book, data.member("Reader A"));
        Loan b = data.pendingRequest(book, data.member("Reader B"));
        CountDownLatch firstHasLock = new CountDownLatch(1);

        Future<?> first = POOL.submit(() -> tx.executeWithoutResult(s -> {
            loanService.approve(a.getId(), null);
            firstHasLock.countDown();
            sleep(1500);
        }));
        firstHasLock.await(10, TimeUnit.SECONDS);
        Future<?> second = POOL.submit(() -> tx.executeWithoutResult(s -> loanService.approve(b.getId(), null)));
        first.get(20, TimeUnit.SECONDS);
        second.get(20, TimeUnit.SECONDS);

        List<LoanStatus> statuses = List.of(loans.findById(a.getId()).orElseThrow().getStatus(),
                loans.findById(b.getId()).orElseThrow().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(LoanStatus.READY, LoanStatus.WAITLISTED);
    }

    @Test
    @DisplayName("paying and waiving the same fee at once: only one wins")
    void concurrentPayAndWaive() throws Exception {
        Book book = data.book("Fee " + System.nanoTime());
        Member member = data.member("Fee Payer");
        Loan loan = data.loan(book, member, LoanStatus.RETURNED, TODAY.minusDays(20), TODAY.minusDays(6), TODAY);
        Fine fine = tx.execute(s -> {
            Fine f = new Fine();
            f.setLoan(loan);
            f.setMember(member);
            f.setAmount(6000);
            f.setDaysLate(6);
            return fines.save(f);
        });
        CountDownLatch firstHasLock = new CountDownLatch(1);
        List<Throwable> failures = new ArrayList<>();

        Future<?> pay = POOL.submit(() -> tx.executeWithoutResult(s -> {
            fineService.pay(fine.getId(), null);
            firstHasLock.countDown();
            sleep(1500);
        }));
        firstHasLock.await(10, TimeUnit.SECONDS);
        Future<?> waive = POOL.submit(() -> tx.executeWithoutResult(s -> fineService.waive(fine.getId(), "Duplicate")));
        pay.get(20, TimeUnit.SECONDS);
        try {
            waive.get(20, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            failures.add(e.getCause());
        }

        assertThat(fines.findById(fine.getId()).orElseThrow().getStatus()).isEqualTo(FineStatus.PAID);
        assertThat(failures).singleElement().isInstanceOf(com.helmifr.library.common.ConflictException.class);
        assertThat(events.findAllByLoanIdOrderByIdAsc(loan.getId()))
                .noneMatch(e -> e.getType() == LoanEventType.FINE_WAIVED);
    }

    @Test
    @DisplayName("a request made while its member record is merged away waits, then is refused: no loan is left on the removed record")
    void requestDuringMerge() throws Exception {
        Book book = data.bookWithCopies("Merge Race " + System.nanoTime(), 1);
        Member keep = data.member("Kept Record");
        Member duplicate = data.member("Duplicate Record");
        CountDownLatch mergeHasLock = new CountDownLatch(1);

        Future<?> merge = POOL.submit(() -> tx.executeWithoutResult(s -> {
            memberMerge.merge(keep.getId(), duplicate.getId());
            mergeHasLock.countDown();
            sleep(1500);
        }));
        mergeHasLock.await(10, TimeUnit.SECONDS);
        Future<?> request = POOL.submit(() -> tx.executeWithoutResult(s -> myLoans.request(duplicate.getId(), book.getId())));
        merge.get(20, TimeUnit.SECONDS);
        Throwable refusal = null;
        try {
            request.get(20, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            refusal = e.getCause();
        }

        assertThat(refusal).isInstanceOf(com.helmifr.library.common.NotFoundException.class);
        assertThat(loans.findAll()).noneMatch(l -> l.getMember().getId().equals(duplicate.getId()));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
