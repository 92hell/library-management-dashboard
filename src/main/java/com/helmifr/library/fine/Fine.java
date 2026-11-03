package com.helmifr.library.fine;

import com.helmifr.library.loan.Loan;
import com.helmifr.library.member.Member;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** A late fee for one loan, charged when the book came back after its due date. */
@Entity
@Getter
@Setter
@Table(name = "fines")
public class Fine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false, updatable = false)
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    private Member member;

    /** In whole units of the configured currency. */
    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    @Column(name = "days_late", nullable = false, updatable = false)
    private int daysLate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private FineStatus status = FineStatus.UNPAID;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    /** Username of the librarian who recorded the payment or waived the fine. */
    @Column(name = "settled_by", length = 50)
    private String settledBy;

    /** Why a fine was waived. */
    @Column(name = "note", length = 255)
    private String note;
}
