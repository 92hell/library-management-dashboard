package com.helmifr.library.receipt;

import com.helmifr.library.loan.Loan;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * A numbered, signed snapshot of a loan and its history. {@code content} is the exact text that was
 * signed (canonical JSON); never changed after issue.
 */
@Entity
@Getter
@Setter
@Table(name = "receipts")
public class Receipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** R-2026-000123; set right after the row is created (it uses the id). */
    @Column(name = "number", length = 20)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false, updatable = false)
    private Loan loan;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "issued_by", nullable = false, length = 50)
    private String issuedBy;

    /** SHA-256 of the loan part only, to tell whether anything changed since the last receipt. */
    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    /** At most {@link #MAX_CONTENT} characters (fits a long loan history comfortably). */
    @Column(name = "content", nullable = false, length = MAX_CONTENT)
    private String content;

    public static final int MAX_CONTENT = 12000;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    /** Base64. */
    @Column(name = "signature", nullable = false, length = 200)
    private String signature;

    @Column(name = "key_id", nullable = false, length = 40)
    private String keyId;
}
