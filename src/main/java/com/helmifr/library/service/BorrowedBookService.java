package com.helmifr.library.service;

import com.helmifr.library.dao.BorrowedBookDAO;
import com.helmifr.library.dao.BookDAO;
import com.helmifr.library.dao.MemberDAO;
import com.helmifr.library.entity.BorrowedBook;
import com.helmifr.library.entity.Book;
import com.helmifr.library.entity.Member;

import com.helmifr.library.entity.request.BorrowedBookRequest;
import com.helmifr.library.entity.view.BorrowedBookListView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class BorrowedBookService {
    @Autowired
    BorrowedBookDAO borrowedBookDAO;
    @Autowired
    BookDAO bookDAO;
    @Autowired
    MemberDAO memberDAO;

    public BorrowedBook findBorrowedBookById(Long id) {
        Optional<BorrowedBook> borrowedBook = borrowedBookDAO.findById(id);
        return borrowedBook.orElse(null);
    }

    public List<BorrowedBookListView> findAllBorrowedBooksDTO() {
        return borrowedBookDAO.findAllBorrowedBooksListView();
    }

    public List<BorrowedBookListView> searchBorrowedBooksDTO(String generalSearchTerm, LocalDate borrowDateFilter) {
        String actualGeneralSearchTerm = (generalSearchTerm != null && !generalSearchTerm.trim().isEmpty()) ? generalSearchTerm.trim() : null;
        return borrowedBookDAO.searchBorrowedBooksListViewByCriteria(actualGeneralSearchTerm, borrowDateFilter);
    }

    @Transactional
    public BorrowedBook saveBorrowedBook(Long id, BorrowedBookRequest requestDTO) {
        BorrowedBook borrowedBook;

        if (id != null) {
            borrowedBook = borrowedBookDAO.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Borrowed Book record not found with ID: " + id));
        } else {
            borrowedBook = new BorrowedBook();
        }

        borrowedBook.setBorrowDate(requestDTO.getBorrowDate());
        borrowedBook.setReturnDate(requestDTO.getReturnDate());

        if (borrowedBook.getId() == null && borrowedBook.getBorrowDate() == null) {
            borrowedBook.setBorrowDate(LocalDate.now());
        }

        if (requestDTO.getBookId() == null) {
            throw new IllegalArgumentException("Book ID is required.");
        }
        Book book = bookDAO.findById(requestDTO.getBookId())
                .orElseThrow(() -> new IllegalArgumentException("Book not found with ID: " + requestDTO.getBookId()));
        borrowedBook.setBook(book);

        if (requestDTO.getMemberId() == null) {
            throw new IllegalArgumentException("Member ID is required.");
        }
        Member member = memberDAO.findById(requestDTO.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("Member not found with ID: " + requestDTO.getMemberId()));
        borrowedBook.setMember(member);

        return borrowedBookDAO.save(borrowedBook);
    }

    public void deleteBorrowedBookById(Long id) {
        BorrowedBook borrowedBook = findBorrowedBookById(id);
        if(borrowedBook != null) {
            borrowedBook.setIsDeleted(true);
            borrowedBookDAO.save(borrowedBook);
        }
    }
}