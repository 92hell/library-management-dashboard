package com.helmifr.library.controller;

import com.helmifr.library.entity.BorrowedBook;
import com.helmifr.library.entity.Book;
import com.helmifr.library.entity.Member;

import com.helmifr.library.entity.request.BorrowedBookRequest;
import com.helmifr.library.entity.view.BorrowedBookListView;
import com.helmifr.library.service.BorrowedBookService;
import com.helmifr.library.service.BookService;
import com.helmifr.library.service.MemberService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/borrowed-books")
public class BorrowedBookController {
    @Autowired
    BorrowedBookService borrowedBookService;
    @Autowired
    BookService bookService;
    @Autowired
    MemberService memberService;


    @GetMapping
    public List<BorrowedBookListView> getBorrowedBooks(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate borrowDateFilter) {
        if ((searchTerm != null && !searchTerm.trim().isEmpty()) || borrowDateFilter != null) {
            return borrowedBookService.searchBorrowedBooksDTO(searchTerm, borrowDateFilter);
        } else {
            return borrowedBookService.findAllBorrowedBooksDTO();
        }
    }

    @GetMapping("/{id}")
    public BorrowedBook getBorrowedBook(@PathVariable long id) {
        return borrowedBookService.findBorrowedBookById(id);
    }

    @GetMapping("/books/active")
    public List<Book> getActiveBooksForDropdown() {
        return bookService.findAllBooks();
    }

    @GetMapping("/members/active")
    public List<Member> getActiveMembersForDropdown() {
        return memberService.findAllMembers();
    }

    @PostMapping
    public ResponseEntity<BorrowedBook> createBorrowedBook(@RequestBody BorrowedBookRequest requestDTO) throws URISyntaxException {
        BorrowedBook saved = borrowedBookService.saveBorrowedBook(null, requestDTO);
        return ResponseEntity.created(new URI("/borrowed-books/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BorrowedBook> updateBorrowedBook(@PathVariable long id, @RequestBody BorrowedBookRequest requestDTO) {
        BorrowedBook updated = borrowedBookService.saveBorrowedBook(id, requestDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBorrowedBook(@PathVariable long id) {
        borrowedBookService.deleteBorrowedBookById(id);
        return ResponseEntity.ok().build();
    }
}