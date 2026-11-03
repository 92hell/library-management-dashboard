package com.helmifr.library.fine;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** One payment towards a late fee (a fee can be paid in parts). Written once, never changed. */
@Entity
@Getter
@Setter
@Table(name = "fine_payments")
public class FinePayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fine_id", nullable = false, updatable = false)
    private Fine fine;

    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt;

    /** Display name of the librarian who took the payment. */
    @Column(name = "recorded_by", nullable = false, length = 50, updatable = false)
    private String recordedBy;
}
