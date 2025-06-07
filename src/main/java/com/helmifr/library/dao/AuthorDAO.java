package com.helmifr.library.dao;

import com.helmifr.library.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuthorDAO extends JpaRepository<Author, Long> {
    List<Author> findAllByIsDeleted(boolean isDeleted);
}
