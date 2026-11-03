package com.helmifr.library.support;

import com.helmifr.library.account.Role;
import com.helmifr.library.account.UserAccount;
import com.helmifr.library.account.UserAccountRepository;
import com.helmifr.library.author.Author;
import com.helmifr.library.author.AuthorRepository;
import com.helmifr.library.book.Book;
import com.helmifr.library.book.BookRepository;
import com.helmifr.library.loan.Loan;
import com.helmifr.library.loan.LoanRepository;
import com.helmifr.library.loan.LoanStatus;
import com.helmifr.library.member.Member;
import com.helmifr.library.member.MemberRepository;
import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Arranges rows directly through repositories, so tests only exercise the API they are about. */
@TestComponent
public class TestData {

    public static final String PASSWORD = "correct-horse-battery";
    private static final AtomicInteger SEQ = new AtomicInteger();

    private final AuthorRepository authors;
    private final BookRepository books;
    private final MemberRepository members;
    private final LoanRepository loans;
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    public TestData(AuthorRepository authors, BookRepository books, MemberRepository members, LoanRepository loans,
                    UserAccountRepository accounts, PasswordEncoder passwordEncoder) {
        this.authors = authors;
        this.books = books;
        this.members = members;
        this.loans = loans;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

    // ---- Catalog -------------------------------------------------------------------------------

    public Author author(String name) {
        Author a = new Author();
        a.setName(name);
        return authors.save(a);
    }

    public Book book(String title) {
        return book(title, author("Author of " + title));
    }

    public Book book(String title, Author author) {
        Book b = new Book();
        b.setTitle(title);
        b.setPublishedDate(LocalDate.of(2020, 1, 1));
        b.setAuthor(author);
        return books.save(b);
    }

    // ---- People --------------------------------------------------------------------------------

    /** A walk-in member with no online account. */
    public Member member(String name) {
        Member m = new Member();
        m.setName(name);
        m.setDateOfBirth(LocalDate.of(1990, 5, 17));
        m.setEmail(slug(name) + "@example.com");
        m.setPhone("081234567890");
        m.setMemberCode("TEST-" + SEQ.incrementAndGet());
        return members.save(m);
    }

    /** A member with a customer account linked to it. Password: {@link #PASSWORD}. */
    public UserAccount customer(String name) {
        Member member = member(name);
        return account(slug(name), Role.CUSTOMER, name, member);
    }

    /** A librarian (staff) account. Password: {@link #PASSWORD}. */
    public UserAccount librarian(String name) {
        return account(slug(name), Role.LIBRARIAN, name, null);
    }

    private UserAccount account(String username, Role role, String displayName, Member member) {
        UserAccount a = new UserAccount();
        a.setUsername(username + "-" + SEQ.incrementAndGet());
        a.setPasswordHash(passwordEncoder.encode(PASSWORD));
        a.setRole(role);
        a.setDisplayName(displayName);
        a.setEmail(member != null ? member.getEmail() : username + "@library.test");
        a.setMember(member);
        return accounts.save(a);
    }

    // ---- Loans ---------------------------------------------------------------------------------

    public Loan activeLoan(Book book, Member member, LocalDate borrowDate, LocalDate dueDate) {
        return loan(book, member, LoanStatus.ACTIVE, borrowDate, dueDate, null);
    }

    public Loan pendingRequest(Book book, Member member) {
        return loan(book, member, LoanStatus.PENDING, null, null, null);
    }

    public Loan loan(Book book, Member member, LoanStatus status, LocalDate borrowDate, LocalDate dueDate,
                     LocalDate returnDate) {
        Loan l = new Loan();
        l.setBook(book);
        l.setMember(member);
        l.setStatus(status);
        l.setBorrowDate(borrowDate);
        l.setDueDate(dueDate);
        l.setReturnDate(returnDate);
        return loans.save(l);
    }

    public Loan reload(Loan loan) {
        return loans.findById(loan.getId()).orElseThrow();
    }

    public long loanCount() {
        return loans.count();
    }

    private static String slug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".");
    }
}
