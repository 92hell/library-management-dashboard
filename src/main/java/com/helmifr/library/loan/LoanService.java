package com.helmifr.library.loan;

import com.helmifr.library.book.Book;
import com.helmifr.library.book.BookRepository;
import com.helmifr.library.book.Copy;
import com.helmifr.library.book.CopyRepository;
import com.helmifr.library.book.CopyService;
import com.helmifr.library.common.Audit;
import com.helmifr.library.common.ConflictException;
import com.helmifr.library.common.FieldValidationException;
import com.helmifr.library.common.NotFoundException;
import com.helmifr.library.common.PageResponse;
import com.helmifr.library.common.SearchPatterns;
import com.helmifr.library.loan.LoanDecisions.Approval;
import com.helmifr.library.loan.LoanDecisions.Rejection;
import com.helmifr.library.member.Member;
import com.helmifr.library.member.MemberService;
import com.helmifr.library.notification.NotificationService;
import com.helmifr.library.notification.NotificationType;
import com.helmifr.library.security.CurrentUser;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loans as handled by librarians: desk checkouts, edits, request decisions and returns. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoanService {

    static final String PENDING_LINK = "/borrowed-books?status=pending";
    static final String MY_LOANS_LINK = "/my/loans";

    private final LoanRepository loans;
    private final BookRepository books;
    private final MemberService memberService;
    private final CopyService copyService;
    private final HoldQueue holdQueue;
    private final LoanViews views;
    private final CopyRepository copies;
    private final NotificationService notifications;
    private final LoanPolicy policy;
    private final Clock clock;

    public PageResponse<LoanResponse> search(String q, LocalDate borrowDate, LoanFilter filter, Pageable pageable) {
        return PageResponse.of(loans.search(SearchPatterns.contains(q), borrowDate, filter.status(),
                        filter == LoanFilter.OVERDUE, today(), pageable)
                .map(views::of));
    }

    public List<LoanResponse> findByMember(Long memberId) {
        return views.ofAll(loans.findAllByMemberIdAndDeletedFalseOrderByIdDesc(memberId));
    }

    public LoanResponse get(Long id) {
        return views.of(require(id));
    }

    /** Desk checkout (or a past loan when a return date is given). */
    @Transactional
    public LoanResponse create(LoanRequest request) {
        Loan loan = new Loan();
        apply(loan, request);
        loan = loans.save(loan);
        Audit.event("LOAN_RECORDED", "loanId={} bookId={} memberId={} status={} by={}", loan.getId(),
                loan.getBook().getId(), loan.getMember().getId(), loan.getStatus(), CurrentUser.username());
        if (loan.getStatus() == LoanStatus.ACTIVE) {
            notifications.notifyMember(loan.getMember().getId(), NotificationType.LOAN_RECORDED,
                    "You borrowed \"%s\". Please return it by %s.".formatted(loan.getBook().getTitle(), loan.getDueDate()),
                    MY_LOANS_LINK);
        }
        return views.of(loan);
    }

    /** Corrects an ACTIVE or RETURNED loan. Requests are handled with approve/reject instead. */
    @Transactional
    public LoanResponse update(Long id, LoanRequest request) {
        Loan loan = require(id);
        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.RETURNED) {
            throw new ConflictException("Only active or returned loans can be edited; this one is " + loan.getStatus() + ".");
        }
        apply(loan, request);
        return views.of(loan);
    }

    /**
     * Approves a request: a free copy is set aside for the member (READY, to collect within the
     * pickup period); if every copy is out, the request joins the queue (WAITLISTED).
     */
    @Transactional
    public LoanResponse approve(Long id, Approval approval) {
        Loan loan = requireStatus(id, LoanStatus.PENDING);
        if (!loan.getMember().isActive()) {
            throw new ConflictException("The member's card is inactive.");
        }
        Book book = lockBook(loan.getBook().getId());
        loan.setDecidedBy(CurrentUser.username());
        var free = holdQueue.freeCopy(book);
        if (free.isPresent() && loans.findQueue(book.getId()).isEmpty()) {
            holdQueue.setAside(loan, free.get(), NotificationType.REQUEST_APPROVED,
                    "Your request for \"%s\" was approved and is ready for pickup until %s.");
        } else {
            loan.setStatus(LoanStatus.WAITLISTED);
            Integer position = views.of(loan).queuePosition();
            Audit.event("REQUEST_WAITLISTED", "loanId={} bookId={} memberId={} position={} by={}", loan.getId(),
                    book.getId(), loan.getMember().getId(), position, CurrentUser.username());
            notifications.notifyMember(loan.getMember().getId(), NotificationType.WAITLISTED,
                    "Your request for \"%s\" was approved. All copies are out; you are number %d in the queue."
                            .formatted(book.getTitle(), position), MY_LOANS_LINK);
        }
        return views.of(loan);
    }

    /** The member collects a copy set aside for them: the loan starts today. */
    @Transactional
    public LoanResponse checkout(Long id, Approval approval) {
        Loan loan = requireStatus(id, LoanStatus.READY);
        if (!loan.getMember().isActive()) {
            throw new ConflictException("The member's card is inactive.");
        }
        LocalDate today = today();
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setBorrowDate(today);
        loan.setDueDate(approval != null && approval.dueDate() != null ? approval.dueDate() : today.plusDays(policy.periodDays()));
        loan.setReadyUntil(null);
        Audit.event("HOLD_COLLECTED", "loanId={} copy={} memberId={} due={} by={}", loan.getId(),
                loan.getCopy().getBarcode(), loan.getMember().getId(), loan.getDueDate(), CurrentUser.username());
        notifications.notifyMember(loan.getMember().getId(), NotificationType.LOAN_RECORDED,
                "You borrowed \"%s\". Please return it by %s.".formatted(loan.getBook().getTitle(), loan.getDueDate()),
                MY_LOANS_LINK);
        return views.of(loan);
    }

    /** Declines a request, also one in the queue or set aside (its copy then goes to the next in line). */
    @Transactional
    public LoanResponse reject(Long id, Rejection rejection) {
        Loan loan = require(id);
        if (loan.getStatus() != LoanStatus.PENDING && loan.getStatus() != LoanStatus.WAITLISTED
                && loan.getStatus() != LoanStatus.READY) {
            throw new ConflictException("Only requests that are not yet lent can be declined; this one is "
                    + loan.getStatus() + ".");
        }
        Book book = lockBook(loan.getBook().getId());
        boolean freesCopy = loan.getStatus() == LoanStatus.READY;
        String reason = rejection == null || rejection.reason() == null || rejection.reason().isBlank()
                ? null : rejection.reason().trim();
        loan.setStatus(LoanStatus.REJECTED);
        loan.setDecisionNote(reason);
        loan.setDecidedBy(CurrentUser.username());
        loan.setReadyUntil(null);
        Audit.event("REQUEST_REJECTED", "loanId={} memberId={} by={}", loan.getId(), loan.getMember().getId(),
                CurrentUser.username());
        notifications.notifyMember(loan.getMember().getId(), NotificationType.REQUEST_REJECTED,
                "Your request for \"%s\" was declined%s".formatted(loan.getBook().getTitle(),
                        reason == null ? "." : ": " + reason),
                MY_LOANS_LINK);
        if (freesCopy) {
            holdQueue.serve(book);
        }
        return views.of(loan);
    }

    /** The book is back: the copy goes to the next in the queue, if anyone is waiting. */
    @Transactional
    public LoanResponse markReturned(Long id) {
        Loan loan = requireStatus(id, LoanStatus.ACTIVE);
        Book book = lockBook(loan.getBook().getId());
        loan.setStatus(LoanStatus.RETURNED);
        loan.setReturnDate(today());
        Audit.event("LOAN_RETURNED", "loanId={} bookId={} memberId={} by={}", loan.getId(), loan.getBook().getId(),
                loan.getMember().getId(), CurrentUser.username());
        holdQueue.serve(book);
        return views.of(loan);
    }

    /** Extends an active loan by one loan period, if the rules allow (see LoanViews.whyNotRenewable). */
    @Transactional
    public LoanResponse renew(Loan loan) {
        lockBook(loan.getBook().getId());
        String refusal = views.whyNotRenewable(loan, today());
        if (refusal != null) {
            throw new ConflictException(refusal);
        }
        loan.setDueDate(loan.getDueDate().plusDays(policy.periodDays()));
        loan.setRenewalCount(loan.getRenewalCount() + 1);
        loan.setDueSoonNotified(false);
        loan.setOverdueNotified(false);
        Audit.event("LOAN_RENEWED", "loanId={} memberId={} due={} by={}", loan.getId(), loan.getMember().getId(),
                loan.getDueDate(), CurrentUser.username());
        return views.of(loan);
    }

    @Transactional
    public LoanResponse renew(Long id) {
        return renew(require(id));
    }

    @Transactional
    public void delete(Long id) {
        Loan loan = require(id);
        Book book = lockBook(loan.getBook().getId());
        boolean freesCopy = loan.getStatus().holdsCopy();
        loan.setDeleted(true);
        Audit.event("LOAN_DELETED", "loanId={} by={}", loan.getId(), CurrentUser.username());
        if (freesCopy) {
            holdQueue.serve(book);
        }
    }

    Loan require(Long id) {
        return loans.findByIdAndDeletedFalse(id).orElseThrow(() -> new NotFoundException("Loan", id));
    }

    private Loan requireStatus(Long id, LoanStatus expected) {
        Loan loan = require(id);
        if (loan.getStatus() != expected) {
            throw new ConflictException("This loan is " + loan.getStatus() + ", not " + expected + ".");
        }
        return loan;
    }

    /** Locks the book row: concurrent checkouts of the same book are serialized here. */
    Book lockBook(Long bookId) {
        return books.findActiveForUpdate(bookId).orElseThrow(() -> new NotFoundException("Book", bookId));
    }

    private void apply(Loan loan, LoanRequest request) {
        LocalDate today = today();
        LocalDate borrowDate = request.borrowDate() != null ? request.borrowDate() : today;
        if (request.returnDate() != null && request.returnDate().isBefore(borrowDate)) {
            throw new FieldValidationException("returnDate", "must not be before the borrow date");
        }
        LocalDate dueDate = request.dueDate() != null ? request.dueDate() : borrowDate.plusDays(policy.periodDays());
        if (dueDate.isBefore(borrowDate)) {
            throw new FieldValidationException("dueDate", "must not be before the borrow date");
        }

        Copy scanned = request.copyBarcode() == null || request.copyBarcode().isBlank()
                ? null : copyService.requireByBarcode(request.copyBarcode());
        if (scanned != null && request.bookId() != null && !scanned.getBook().getId().equals(request.bookId())) {
            throw new FieldValidationException("copyBarcode", "belongs to another book");
        }
        Book book = lockBook(scanned != null ? scanned.getBook().getId() : request.bookId());
        LoanStatus status = request.returnDate() == null ? LoanStatus.ACTIVE : LoanStatus.RETURNED;
        Copy copy = chooseCopy(loan, book, scanned, status);

        Member member = memberService.require(request.memberId());
        boolean memberChanged = loan.getMember() == null || !Objects.equals(loan.getMember().getId(), member.getId());
        if (memberChanged && !member.isActive()) {
            throw new FieldValidationException("memberId", "Member is inactive and cannot borrow books");
        }

        boolean datesChanged = !Objects.equals(loan.getDueDate(), dueDate);
        loan.setBook(book);
        loan.setCopy(copy);
        loan.setMember(member);
        loan.setStatus(status);
        loan.setBorrowDate(borrowDate);
        loan.setDueDate(dueDate);
        loan.setReturnDate(request.returnDate());
        if (datesChanged) {
            loan.setDueSoonNotified(false);
            loan.setOverdueNotified(false);
        }
    }

    /**
     * The scanned copy if one was given; otherwise the loan's current copy when it still fits;
     * otherwise a free copy (any copy for a past, returned loan).
     */
    private Copy chooseCopy(Loan loan, Book book, Copy scanned, LoanStatus status) {
        if (scanned != null) {
            if (status == LoanStatus.ACTIVE && loans.isCopyOnLoan(scanned.getId(), loan.getId())) {
                throw new ConflictException("Copy " + scanned.getBarcode() + " is already on loan.");
            }
            return scanned;
        }
        Copy current = loan.getCopy();
        if (current != null && current.getBook().getId().equals(book.getId())
                && (status != LoanStatus.ACTIVE || !loans.isCopyOnLoan(current.getId(), loan.getId()))) {
            return current;
        }
        if (status == LoanStatus.ACTIVE) {
            return availableCopy(book, loan.getId());
        }
        return copies.findAllByBookIdAndDeletedFalseOrderByIdAsc(book.getId()).stream().findFirst()
                .orElseThrow(() -> new FieldValidationException("bookId", "This title has no copies"));
    }

    /** A copy of the book that is on the shelf; the caller holds the book's lock. */
    private Copy availableCopy(Book book, Long excludeLoanId) {
        return copies.findAllByBookIdAndDeletedFalseOrderByIdAsc(book.getId()).stream()
                .filter(c -> !loans.isCopyOnLoan(c.getId(), excludeLoanId))
                .findFirst()
                .orElseThrow(() -> new ConflictException("No copy of \"" + book.getTitle() + "\" is available."));
    }

    /** Desk return: scan the copy, its active loan is closed. */
    @Transactional
    public LoanResponse returnByBarcode(String barcode) {
        Copy copy = copyService.requireByBarcode(barcode);
        Loan loan = loans.findActiveByCopy(copy.getId())
                .orElseThrow(() -> new ConflictException("Copy " + copy.getBarcode() + " is not on loan."));
        return markReturned(loan.getId());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
