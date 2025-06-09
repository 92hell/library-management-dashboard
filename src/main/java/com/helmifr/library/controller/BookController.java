package com.helmifr.library.controller;

import com.helmifr.library.entity.Book;
import com.helmifr.library.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@RestController
@RequestMapping("/books")
public class BookController {
    @Autowired
    BookService bookService;

    @GetMapping
    public List<Book> getBooks(@RequestParam(required = false) String searchTerm) {
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            return bookService.searchBooks(searchTerm);
        } else {
            return bookService.findAllBooks();
        }
    }

    @GetMapping("/authoredBy/{authorId}")
    public List<Book> getAuthoredBy(@PathVariable long authorId) {
        return bookService.findAllAuthoredBooks(authorId);
    }

    @GetMapping("/categories")
    public List<String> getBookCategories() {
        return bookService.getBookCategories();
    }

    @GetMapping("/{id}")
    public Book getBook(@PathVariable long id) {
        return bookService.findBookById(id);
    }

    @PostMapping
    public ResponseEntity<Book> createBook(@RequestBody Book book) throws URISyntaxException {
        Book saved = bookService.saveBook(book);
        return ResponseEntity.created(new URI("/books/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Book> updateBook(@PathVariable long id, @RequestBody Book book) {
        if(bookService.findBookById(id) != null) {
            Book updated = bookService.saveBook(book);
            return ResponseEntity.ok(updated);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Book> deleteBook(@PathVariable long id) {
        bookService.deleteBookById(id);
        return ResponseEntity.ok().build();
    }
}
