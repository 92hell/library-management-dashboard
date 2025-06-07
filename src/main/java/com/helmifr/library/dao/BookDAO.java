package com.helmifr.library.dao;

import com.helmifr.library.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookDAO extends JpaRepository<Book, Long> {
    List<Book> findAllByIsDeletedFalse();
}
