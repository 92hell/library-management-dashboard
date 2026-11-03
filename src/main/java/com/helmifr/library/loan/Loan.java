package com.helmifr.library.loan;

import com.helmifr.library.book.Book;
import com.helmifr.library.member.Member;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** One book for one member, from request (or desk checkout) to return. See {@link LoanStatus}. */
@Entity
@Getter
@Setter
@Table(name = "borrowed_books")
public class Loan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LoanStatus status = LoanStatus.ACTIVE;

    /** When the customer asked for the book online (null for desk checkouts). */
    @Column(name = "requested_at")
    private Instant requestedAt;

    /** Null while PENDING. */
    @Column(name = "borrow_date")
    private LocalDate borrowDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "return_date")
    private LocalDate returnDate;

    /** Username of the librarian who approved or rejected the request. */
    @Column(name = "decided_by", length = 50)
    private String decidedBy;

    /** Reason shown to the customer when a request is rejected. */
    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "due_soon_notified", nullable = false)
    private boolean dueSoonNotified;

    @Column(name = "overdue_notified", nullable = false)
    private boolean overdueNotified;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public boolean isOverdue(LocalDate today) {
        return status == LoanStatus.ACTIVE && dueDate != null && dueDate.isBefore(today);
    }
}
