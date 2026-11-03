package com.helmifr.library.book;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Request/response shapes for copies (librarian only). */
public final class CopyDtos {
    private CopyDtos() {}

    /** Barcode is optional when adding a copy (one is generated); case-insensitive. */
    public record CopyRequest(
            @Pattern(regexp = "^\\s*[A-Za-z0-9-]{4,32}\\s*$",
                    message = "{library.validation.barcode}") String barcode,
            @Size(max = 50) String shelfLocation) {}

    public enum CopyStatus { AVAILABLE, ON_LOAN }

    /** @param held set aside on the holds shelf (READY) rather than lent; then readyUntil is set */
    public record CurrentLoan(Long id, Long memberId, String memberName, LocalDate dueDate, boolean overdue,
                              boolean held, LocalDate readyUntil) {}

    public record CopyResponse(Long id, String barcode, String shelfLocation, CopyStatus status, CurrentLoan loan) {}

    public record CopyBook(Long id, String title, String authorName) {}

    /** What the desk sees after scanning a barcode. */
    public record CopyLookup(Long id, String barcode, String shelfLocation, CopyBook book, CopyStatus status,
                             CurrentLoan loan) {}
}
