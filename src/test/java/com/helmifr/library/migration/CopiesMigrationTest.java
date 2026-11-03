package com.helmifr.library.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V4 introduces physical copies. A database that already has books and loans (schema V3) must
 * come out with one copy per book, and every lent or returned loan pointing at that copy.
 */
@DisplayName("Migration V4: existing books get a copy, existing loans point at it")
class CopiesMigrationTest {

    @Test
    void backfillsCopiesAndLoans() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:migration-v4;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        Flyway.configure().dataSource(ds).target("3").load().migrate();
        JdbcTemplate db = new JdbcTemplate(ds);
        db.update("INSERT INTO authors (id, name) VALUES (1, 'A')");
        db.update("INSERT INTO books (id, title, published_date, author_id) VALUES (10, 'Lent', '2020-01-01', 1)");
        db.update("INSERT INTO books (id, title, published_date, author_id) VALUES (11, 'Requested', '2020-01-01', 1)");
        db.update("INSERT INTO books (id, title, published_date, author_id, is_deleted) VALUES (12, 'Gone', '2020-01-01', 1, TRUE)");
        db.update("INSERT INTO members (id, name, date_of_birth, email, phone, library_card_id) VALUES (5, 'M', '1990-01-01', 'm@x.io', '0812', 'LIB-5')");
        db.update("INSERT INTO borrowed_books (id, book_id, member_id, borrow_date, status) VALUES (100, 10, 5, '2026-10-01', 'ACTIVE')");
        db.update("INSERT INTO borrowed_books (id, book_id, member_id, status) VALUES (101, 11, 5, 'PENDING')");

        Flyway.configure().dataSource(ds).load().migrate();

        List<Map<String, Object>> copies = db.queryForList(
                "SELECT book_id, barcode, is_deleted FROM book_copies ORDER BY book_id");
        assertThat(copies).hasSize(3);
        assertThat(copies.get(0)).containsEntry("barcode", "BC-10");
        assertThat(copies.get(2)).containsEntry("is_deleted", true);
        Long copyOfLent = db.queryForObject("SELECT id FROM book_copies WHERE book_id = 10", Long.class);
        assertThat(db.queryForObject("SELECT copy_id FROM borrowed_books WHERE id = 100", Long.class)).isEqualTo(copyOfLent);
        assertThat(db.queryForObject("SELECT copy_id FROM borrowed_books WHERE id = 101", Long.class))
                .as("a pending request has no copy yet").isNull();
    }
}
