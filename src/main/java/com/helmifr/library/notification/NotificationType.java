package com.helmifr.library.notification;

/** What a notification is about; {@link #title()} is used as e.g. the email subject. */
public enum NotificationType {
    /** To librarians: a customer asked to borrow a book. */
    BORROW_REQUESTED("New borrow request"),
    /** To the customer: their request was approved (the book is lent to them). */
    REQUEST_APPROVED("Request approved"),
    /** To the customer: their request was declined, with the reason. */
    REQUEST_REJECTED("Request declined"),
    /** To the customer: a librarian recorded a desk checkout for them. */
    LOAN_RECORDED("Loan recorded"),
    /** To the customer: a book is due within the next few days. */
    DUE_SOON("Book due soon"),
    /** To the customer: a book is past its due date. */
    OVERDUE("Overdue book");

    private final String title;

    NotificationType(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
