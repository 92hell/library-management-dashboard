package com.helmifr.library.member;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A library member, identified by their library card ID. A walk-in (unregistered) member has
 * no online account; registering links an account, verified by the email or phone on file.
 * Loans are reached through {@link com.helmifr.library.loan.Loan}; there is
 * deliberately no collection mapping onto borrowed_books here (the old many-to-many mapping
 * deleted a member's loans whenever the member was updated).
 */
@Entity
@Getter
@Setter
@Table(name = "members")
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The customer's key, printed on their library card, e.g. LIB-7KQ2XM. Changes only when the card is replaced. */
    @Column(name = "library_card_id", nullable = false, length = 20)
    private String libraryCardId;

    /** Last day the card can be used; null = it does not expire. Renewed at the desk. */
    @Column(name = "card_expires_on")
    private LocalDate cardExpiresOn;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    /** Has the card expired by {@code today}? It still works on its last day. */
    public boolean cardExpiredOn(LocalDate today) {
        return cardExpiresOn != null && today.isAfter(cardExpiresOn);
    }

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
