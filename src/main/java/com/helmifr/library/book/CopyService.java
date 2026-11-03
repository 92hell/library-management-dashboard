package com.helmifr.library.book;

import com.helmifr.library.book.CopyDtos.CopyBook;
import com.helmifr.library.book.CopyDtos.CopyLookup;
import com.helmifr.library.book.CopyDtos.CopyRequest;
import com.helmifr.library.book.CopyDtos.CopyResponse;
import com.helmifr.library.book.CopyDtos.CopyStatus;
import com.helmifr.library.book.CopyDtos.CurrentLoan;
import com.helmifr.library.common.Audit;
import com.helmifr.library.common.ConflictException;
import com.helmifr.library.common.FieldValidationException;
import com.helmifr.library.common.NotFoundException;
import com.helmifr.library.loan.Loan;
import com.helmifr.library.loan.LoanRepository;
import com.helmifr.library.security.CurrentUser;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Physical copies: availability per title, copy management and barcode lookups. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CopyService {

    /** How many copies a title has in the collection and how many are on the shelf right now. */
    public record Availability(long total, long available) {
        static final Availability NONE = new Availability(0, 0);

        public boolean allOut() {
            return total > 0 && available == 0;
        }
    }

    private final CopyRepository copies;
    private final BookRepository books;
    private final LoanRepository loans;
    private final CopyBarcodes barcodes;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public Map<Long, Availability> availability(Collection<Long> bookIds) {
        if (bookIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> total = toMap(copies.countByBook(bookIds));
        Map<Long, Long> lent = toMap(loans.countOnLoanByBook(bookIds));
        Map<Long, Availability> result = new HashMap<>();
        for (Long id : bookIds) {
            long t = total.getOrDefault(id, 0L);
            result.put(id, new Availability(t, Math.max(0, t - lent.getOrDefault(id, 0L))));
        }
        return result;
    }

    public Availability availability(Long bookId) {
        return availability(List.of(bookId)).getOrDefault(bookId, Availability.NONE);
    }

    public List<CopyResponse> copiesOf(Long bookId) {
        requireBook(bookId);
        List<Copy> list = copies.findAllByBookIdAndDeletedFalseOrderByIdAsc(bookId);
        Map<Long, Loan> lentBy = list.isEmpty() ? Map.of() : loans.findActiveByCopies(list.stream().map(Copy::getId).toList())
                .stream().collect(Collectors.toMap(l -> l.getCopy().getId(), Function.identity()));
        return list.stream().map(c -> toResponse(c, lentBy.get(c.getId()))).toList();
    }

    public CopyLookup lookup(String barcode) {
        Copy copy = requireByBarcode(barcode);
        Loan loan = loans.findActiveByCopy(copy.getId()).orElse(null);
        Book book = copy.getBook();
        return new CopyLookup(copy.getId(), copy.getBarcode(), copy.getShelfLocation(),
                new CopyBook(book.getId(), book.getTitle(), book.getAuthor().getName()),
                loan == null ? CopyStatus.AVAILABLE : CopyStatus.ON_LOAN, currentLoan(loan));
    }

    @Transactional
    public CopyResponse add(Long bookId, CopyRequest request) {
        Book book = lockBook(bookId);
        Copy copy = new Copy();
        copy.setBook(book);
        copy.setBarcode(request.barcode() == null || request.barcode().isBlank()
                ? barcodes.next() : requireFreeBarcode(request.barcode(), null));
        copy.setShelfLocation(blankToNull(request.shelfLocation()));
        copy = copies.save(copy);
        Audit.event("COPY_ADDED", "bookId={} barcode={} by={}", bookId, copy.getBarcode(), CurrentUser.username());
        events.publishEvent(new CopyAdded(book));
        return toResponse(copy, loans.findActiveByCopies(List.of(copy.getId())).stream().findFirst().orElse(null));
    }

    /** Adds {@code count} copies with generated barcodes (new books). */
    @Transactional
    public void addGenerated(Book book, int count) {
        for (int i = 0; i < count; i++) {
            Copy copy = new Copy();
            copy.setBook(book);
            copy.setBarcode(barcodes.next());
            copies.save(copy);
        }
    }

    @Transactional
    public CopyResponse update(Long copyId, CopyRequest request) {
        Copy copy = require(copyId);
        if (request.barcode() != null && !request.barcode().isBlank()) {
            copy.setBarcode(requireFreeBarcode(request.barcode(), copy.getId()));
        }
        copy.setShelfLocation(blankToNull(request.shelfLocation()));
        return toResponse(copy, loans.findActiveByCopy(copy.getId()).orElse(null));
    }

    /** Withdraws a copy from the collection (lost, damaged, discarded). */
    @Transactional
    public void withdraw(Long copyId) {
        Copy copy = require(copyId);
        lockBook(copy.getBook().getId());
        if (loans.isCopyOnLoan(copyId, null)) {
            throw new ConflictException("Copy " + copy.getBarcode() + " is on loan. Record its return first.");
        }
        copy.setDeleted(true);
        Audit.event("COPY_WITHDRAWN", "copyId={} barcode={} by={}", copyId, copy.getBarcode(), CurrentUser.username());
    }

    @Transactional
    public void withdrawAllOf(Long bookId) {
        copies.withdrawAllOfBook(bookId);
    }

    public Copy require(Long copyId) {
        return copies.findByIdAndDeletedFalse(copyId).orElseThrow(() -> new NotFoundException("Copy", copyId));
    }

    public Copy requireByBarcode(String barcode) {
        String normalized = CopyBarcodes.normalize(barcode);
        return copies.findByBarcodeAndDeletedFalse(normalized)
                .orElseThrow(() -> new NotFoundException("No copy has barcode " + normalized));
    }

    private String requireFreeBarcode(String barcode, Long exceptCopyId) {
        String normalized = CopyBarcodes.normalize(barcode);
        if (!CopyBarcodes.VALID.matcher(normalized).matches()) {
            throw new FieldValidationException("barcode", "must be 4-32 letters, digits or dashes");
        }
        // Barcodes are unique across all copies ever, including withdrawn ones (database constraint).
        copies.findByBarcode(normalized)
                .filter(other -> !other.getId().equals(exceptCopyId))
                .ifPresent(other -> {
                    throw new FieldValidationException("barcode", "is already used by another copy");
                });
        return normalized;
    }

    /** Copies are added and withdrawn under the book's row lock, like loans (see LoanService.lockBook). */
    private Book lockBook(Long bookId) {
        return books.findActiveForUpdate(bookId).orElseThrow(() -> new NotFoundException("Book", bookId));
    }

    private Book requireBook(Long bookId) {
        return books.findByIdAndDeletedFalse(bookId).orElseThrow(() -> new NotFoundException("Book", bookId));
    }

    private CopyResponse toResponse(Copy c, Loan loan) {
        return new CopyResponse(c.getId(), c.getBarcode(), c.getShelfLocation(),
                loan == null ? CopyStatus.AVAILABLE : CopyStatus.ON_LOAN, currentLoan(loan));
    }

    private CurrentLoan currentLoan(Loan loan) {
        if (loan == null) {
            return null;
        }
        return new CurrentLoan(loan.getId(), loan.getMember().getId(), loan.getMember().getName(), loan.getDueDate(),
                loan.isOverdue(LocalDate.now(clock)), loan.getStatus() == com.helmifr.library.loan.LoanStatus.READY,
                loan.getReadyUntil());
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
