package com.helmifr.library.notification;

public enum NotificationType {
    /** To librarians: a customer asked to borrow a book. */
    BORROW_REQUESTED,
    /** To the customer: their request was approved (the book is lent to them). */
    REQUEST_APPROVED,
    /** To the customer: their request was declined, with the reason. */
    REQUEST_REJECTED,
    /** To the customer: a librarian recorded a desk checkout for them. */
    LOAN_RECORDED,
    /** To the customer: a book is due within the next few days. */
    DUE_SOON,
    /** To the customer: a book is past its due date. */
    OVERDUE
}
