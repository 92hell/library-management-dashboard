package com.helmifr.library.dao;

import com.helmifr.library.entity.BorrowedBook;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BorrowedBookDAO extends JpaRepository<BorrowedBook, Long> {
}
