package com.helmifr.library.demo;

import com.helmifr.library.account.AccountService;
import com.helmifr.library.account.Role;
import com.helmifr.library.account.UserAccount;
import com.helmifr.library.account.UserAccountRepository;
import com.helmifr.library.author.Author;
import com.helmifr.library.author.AuthorRepository;
import com.helmifr.library.book.Book;
import com.helmifr.library.book.BookRepository;
import com.helmifr.library.book.Copy;
import com.helmifr.library.book.CopyBarcodes;
import com.helmifr.library.book.CopyRepository;
import com.helmifr.library.loan.Loan;
import com.helmifr.library.loan.LoanRepository;
import com.helmifr.library.loan.LoanStatus;
import com.helmifr.library.member.Member;
import com.helmifr.library.member.LibraryCardIds;
import com.helmifr.library.member.MemberRepository;
import com.helmifr.library.common.Audit;
import com.helmifr.library.notification.NotificationRepository;
import com.helmifr.library.security.SecurityProperties;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo data: {@link #seed()} creates the demo accounts and, on an empty catalogue, a small sample
 * library (safe to repeat); {@link #reset()} wipes everything except the owner's account and seeds
 * again, undoing whatever visitors did.
 */
@Component
@RequiredArgsConstructor
public class DemoDataSeeder {

    public static final String DEMO_CUSTOMER = "demo";
    public static final String DEMO_LIBRARIAN = "demo-librarian";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final DemoProperties properties;
    private final UserAccountRepository accounts;
    private final AccountService accountService;
    private final AuthorRepository authors;
    private final BookRepository books;
    private final MemberRepository members;
    private final LibraryCardIds libraryCardIds;
    private final LoanRepository loans;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final NotificationRepository notifications;
    private final CopyRepository copies;
    private final CopyBarcodes barcodes;
    private final SecurityProperties securityProperties;
    private final EntityManager entityManager;

    /** Deletes all data except the owner librarian's account, then seeds the demo again. */
    @Transactional
    public void reset() {
        notifications.deleteAllInBatch();
        loans.deleteAllInBatch();
        accounts.deleteAllExcept(securityProperties.librarian().username());
        members.deleteAllInBatch();
        copies.deleteAllInBatch();
        books.deleteAllInBatch();
        authors.deleteAllInBatch();
        entityManager.flush();
        entityManager.clear();
        seed();
        Audit.event("DEMO_RESET", "demo data restored");
    }

    @Transactional
    public void seed() {
        String password = properties.password() == null || properties.password().isBlank()
                ? "demo-password" : properties.password();

        if (!accounts.existsByUsernameIgnoreCase(DEMO_LIBRARIAN)) {
            accountService.newLibrarian(DEMO_LIBRARIAN, password, "Demo Librarian", null);
        }
        UserAccount customer = accounts.findByUsernameIgnoreCase(DEMO_CUSTOMER)
                .orElseGet(() -> createDemoCustomer(password));

        if (books.count() == 0) {
            seedCatalogue(customer.getMember());
            log.info("Seeded demo catalogue");
        }
    }

    private UserAccount createDemoCustomer(String password) {
        Member member = member("Dewi Lestari", "dewi@example.com", LocalDate.of(1995, 3, 14));
        UserAccount account = new UserAccount();
        account.setUsername(DEMO_CUSTOMER);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setRole(Role.CUSTOMER);
        account.setDisplayName(member.getName());
        account.setEmail(member.getEmail());
        account.setMember(member);
        return accounts.save(account);
    }

    private void seedCatalogue(Member demoCustomer) {
        Author pram = author("Pramoedya Ananta Toer");
        Author hirata = author("Andrea Hirata");
        Author eka = author("Eka Kurniawan");
        Author dee = author("Dee Lestari");
        Author tohari = author("Ahmad Tohari");

        // Popular titles have several copies, as in a real library.
        Book bumi = book("Bumi Manusia", pram, 1980, "Historical Fiction,Classics", 2, "FIC PRA");
        book("Anak Semua Bangsa", pram, 1980, "Historical Fiction,Classics", 1, "FIC PRA");
        Book laskar = book("Laskar Pelangi", hirata, 2005, "Fiction,Young Adult", 3, "FIC HIR");
        book("Sang Pemimpi", hirata, 2006, "Fiction,Young Adult", 2, "FIC HIR");
        Book cantik = book("Cantik Itu Luka", eka, 2002, "Fiction,Historical Fiction", 1, "FIC KUR");
        book("Supernova", dee, 2001, "Science Fiction,Fiction", 2, "SF LES");
        book("Ronggeng Dukuh Paruk", tohari, 1982, "Fiction,Classics", 1, "FIC TOH");

        LocalDate today = LocalDate.now(clock);
        Member walkIn = member("Budi Santoso", "budi@example.com", LocalDate.of(1988, 8, 2));
        loan(laskar, demoCustomer, LoanStatus.ACTIVE, today.minusDays(12), today.plusDays(2));
        loan(cantik, walkIn, LoanStatus.ACTIVE, today.minusDays(20), today.minusDays(6));
        Loan request = loan(bumi, demoCustomer, LoanStatus.PENDING, null, null);
        request.setRequestedAt(clock.instant());
    }

    private Author author(String name) {
        Author a = new Author();
        a.setName(name);
        return authors.save(a);
    }

    private Book book(String title, Author author, int year, String categories, int copyCount, String shelf) {
        Book b = new Book();
        b.setTitle(title);
        b.setAuthor(author);
        b.setPublishedDate(LocalDate.of(year, 1, 1));
        b.setCategories(categories);
        b = books.save(b);
        for (int i = 0; i < copyCount; i++) {
            Copy c = new Copy();
            c.setBook(b);
            c.setBarcode(barcodes.next());
            c.setShelfLocation(shelf);
            copies.save(c);
        }
        return b;
    }

    private Member member(String name, String email, LocalDate dateOfBirth) {
        Member m = new Member();
        m.setName(name);
        m.setEmail(email);
        m.setPhone("081200000000");
        m.setDateOfBirth(dateOfBirth);
        m.setLibraryCardId(libraryCardIds.next());
        return members.save(m);
    }

    private Loan loan(Book book, Member member, LoanStatus status, LocalDate borrowDate, LocalDate dueDate) {
        Loan l = new Loan();
        l.setBook(book);
        l.setMember(member);
        l.setStatus(status);
        l.setBorrowDate(borrowDate);
        l.setDueDate(dueDate);
        if (status == LoanStatus.ACTIVE) {
            // Seeded loans use different titles, so the first copy is free.
            l.setCopy(copies.findAllByBookIdAndDeletedFalseOrderByIdAsc(book.getId()).get(0));
        }
        return loans.save(l);
    }
}
