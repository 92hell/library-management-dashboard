package com.helmifr.library.loan;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** One entry in a loan's history. Written once, never changed. */
@Entity
@Getter
@Setter
@Table(name = "loan_events")
public class LoanEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false, updatable = false)
    private Loan loan;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32, updatable = false)
    private LoanEventType type;

    /** The loan's status right after this event. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16, updatable = false)
    private LoanStatus status;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** Username of who did it, or "system" for scheduled jobs. */
    @Column(name = "actor", nullable = false, length = 50, updatable = false)
    private String actor;

    @Column(name = "detail", length = 255, updatable = false)
    private String detail;
}
