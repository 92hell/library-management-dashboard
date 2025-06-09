package com.helmifr.library.service;

import com.helmifr.library.dao.BookDAO;
import com.helmifr.library.entity.Book;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Arrays;
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

    public List<Book> findAllAuthoredBooks(Long authorId) {
        return bookDAO.findAllByAuthorIdAndIsDeletedFalse(authorId);
    }

    public List<Book> searchBooks(String searchTerm) {
        String actualSearchTerm = (searchTerm != null && !searchTerm.trim().isEmpty()) ? searchTerm.trim() : null;
        return bookDAO.searchBooksByCriteria(actualSearchTerm);
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

    public List<String> getBookCategories() {
        return Arrays.asList(
                "Fiction",
                "Non-Fiction",
                "Mystery",
                "Thriller",
                "Fantasy",
                "Science Fiction",
                "Romance",
                "Historical Fiction",
                "Horror",
                "Biography & Autobiography",
                "History",
                "Self-Help",
                "Cookbooks",
                "Travel",
                "Art & Photography",
                "Science & Technology",
                "Business & Economics",
                "Health & Fitness",
                "Religion & Spirituality",
                "Philosophy",
                "Poetry",
                "Comics & Graphic Novels",
                "Young Adult",
                "Children's Books",
                "Education",
                "True Crime",
                "Memoir",
                "Classics",
                "Dystopian"
        );
    }
}
