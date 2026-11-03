package com.helmifr.library.notification;

/** What a notification is about; {@link #title()} is used as e.g. the email subject. */
public enum NotificationType {
    /** To librarians: a customer asked to borrow a book. */
    BORROW_REQUESTED("New borrow request"),
    /** To the customer: their request was approved (the book is lent to them). */
    REQUEST_APPROVED("Request approved"),
    /** To the customer: their request was declined, with the reason. */
    REQUEST_REJECTED("Request declined"),
    /** To the customer: their request was approved but every copy is out; they are in the queue. */
    WAITLISTED("In the queue"),
    /** To the customer: a copy came back and is set aside for them. */
    HOLD_READY("Ready for pickup"),
    /** To the customer: they did not collect a copy set aside for them in time. */
    HOLD_EXPIRED("Hold expired"),
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
