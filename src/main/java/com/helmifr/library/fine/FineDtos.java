package com.helmifr.library.fine;

import com.helmifr.library.common.Ref;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class FineDtos {
    private FineDtos() {}

    public record FineBook(Long id, String title) {}

    public record PaymentView(long amount, Instant paidAt, String recordedBy) {}

    /**
     * @param paidAmount  paid so far
     * @param outstanding still to pay (0 once paid or waived)
     * @param payments    every payment, oldest first
     */
    public record FineResponse(Long id, Long loanId, FineBook book, Ref member, String libraryCardId, long amount,
                               String currency, int daysLate, LocalDate dueDate, LocalDate returnDate,
                               FineStatus status, Instant createdAt, Instant settledAt, String settledBy, String note,
                               long paidAmount, long outstanding, List<PaymentView> payments) {

        static FineResponse from(Fine f, String currency, List<PaymentView> payments) {
            var loan = f.getLoan();
            return new FineResponse(f.getId(), loan.getId(), new FineBook(loan.getBook().getId(), loan.getBook().getTitle()),
                    new Ref(f.getMember().getId(), f.getMember().getName()), f.getMember().getLibraryCardId(),
                    f.getAmount(), currency, f.getDaysLate(), loan.getDueDate(), loan.getReturnDate(), f.getStatus(),
                    f.getCreatedAt(), f.getSettledAt(), f.getSettledBy(), f.getNote(), f.getPaidAmount(),
                    f.outstanding(), payments);
        }
    }

    /** A payment towards a fee; without an amount, everything still due is paid. */
    public record PaymentRequest(Long amount) {}

    /**
     * A member's own fines.
     *
     * @param balance unpaid total
     * @param blocked the balance is high enough that they cannot borrow until they pay
     * @param blockAt the balance at which borrowing stops (0 = never)
     */
    public record MyFines(long balance, String currency, boolean blocked, long blockAt, List<FineResponse> fines) {}

    /**
     * The fee on a loan: charged (UNPAID/PAID/WAIVED) or, for a book still out, ACCRUING so far.
     *
     * @param outstanding what is still to pay (the amount while accruing)
     */
    public record LoanFine(long amount, String currency, FineStatus status, long outstanding) {}

    public record Waiver(@NotBlank @Size(max = 255) String reason) {}
}
