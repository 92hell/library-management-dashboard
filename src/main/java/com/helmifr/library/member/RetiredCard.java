package com.helmifr.library.member;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** A card ID a member no longer uses. It still finds the member and is never issued again. */
@Entity
@Getter
@Setter
@Table(name = "retired_cards")
public class RetiredCard {

    public enum Reason {
        /** Lost or damaged; the member got a new card. */
        REPLACED,
        /** The card of a duplicate member record merged into this one. */
        MERGED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "library_card_id", nullable = false, length = 20, updatable = false)
    private String libraryCardId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20, updatable = false)
    private Reason reason;

    @Column(name = "note", length = 500, updatable = false)
    private String note;

    @Column(name = "retired_at", nullable = false, updatable = false)
    private Instant retiredAt;

    @Column(name = "retired_by", nullable = false, length = 50, updatable = false)
    private String retiredBy;
}
