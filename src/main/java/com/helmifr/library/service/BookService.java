package com.helmifr.library.service;

import com.helmifr.library.dao.BookDAO;
import com.helmifr.library.entity.Book;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BookService {
    @Autowired
    BookDAO bookDAO;

    public Book findBookById(Long id) {
        Optional<Book> book = bookDAO.findById(id);
        return book.orElse(null);
    }

    public List<Book> findAllBooks() {
        return bookDAO.findAllByIsDeletedFalse();
    }

    public List<Book> searchBooks(Book book) {
        return bookDAO.findAll(Example.of(book,
                ExampleMatcher.matching()
                        .withIgnoreCase()
                        .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
        ), Sort.by(Sort.Order.desc("created_at")));
    }

    public Book saveBook(Book book) {
        return bookDAO.save(book);
    }

    public void deleteBookById(Long id) {
        Book book = findBookById(id);
        if(book != null) {
            book.setIsDeleted(true);
            bookDAO.save(book);
        }
    }
}
