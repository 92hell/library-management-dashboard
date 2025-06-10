package com.helmifr.library.entity.view;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BorrowedBookListView {
    private Long id;
    private String bookTitle;
    private String bookAuthorName;
    private String memberName;
    private LocalDate borrowDate;
    private LocalDate returnDate;
}
