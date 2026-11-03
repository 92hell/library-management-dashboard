package com.helmifr.library.member;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Library member (the "library card"). May or may not have an online customer account.
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

    /** Library card number, e.g. LIB-7KQ2XM. Customers use it to link an online account. */
    @Column(name = "member_code", nullable = false, length = 20, updatable = false)
    private String memberCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone", nullable = false, length = 15)
    private String phone;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
