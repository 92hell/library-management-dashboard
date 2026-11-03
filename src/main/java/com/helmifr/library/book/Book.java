package com.helmifr.library.book;

import com.helmifr.library.author.Author;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@Setter
@Table(name = "books")
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "published_date", nullable = false)
    private LocalDate publishedDate;

    @Column(name = "publisher_name")
    private String publisherName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Author author;

    /** Comma-separated, kept as a string column for compatibility with existing data. */
    @Column(name = "categories", length = 1000)
    private String categories;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public List<String> getCategoryList() {
        if (categories == null || categories.isBlank()) {
            return List.of();
        }
        return Arrays.stream(categories.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public void setCategoryList(List<String> list) {
        this.categories = list == null || list.isEmpty() ? null : String.join(",", list);
    }
}
