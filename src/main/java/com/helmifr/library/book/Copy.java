package com.helmifr.library.book;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** One physical copy of a book, identified by the barcode on its label. */
@Entity
@Getter
@Setter
@Table(name = "book_copies")
public class Copy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "barcode", nullable = false, length = 32)
    private String barcode;

    /** Where it lives on the shelves, e.g. "FIC HIR". */
    @Column(name = "shelf_location", length = 50)
    private String shelfLocation;

    /** Withdrawn from the collection (lost, damaged, discarded). */
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
