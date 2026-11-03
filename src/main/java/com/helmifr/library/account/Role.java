package com.helmifr.library.account;

/** The two kinds of user. Stored by name in user_accounts.role. */
public enum Role {
    /** Staff: manages the catalogue, members and loans, and decides borrow requests. */
    LIBRARIAN,
    /** Library member with an online account: browses, requests books, sees their own loans. */
    CUSTOMER
}
