package com.helmifr.library.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class MemberCardDtos {
    private MemberCardDtos() {}

    /** Why the card is replaced, e.g. "Lost" (kept in the card history). */
    public record ReplaceCardRequest(@NotBlank @Size(max = 500) String reason) {}

    /** The duplicate record whose loans, fines and account move to the member kept. */
    public record MergeRequest(@NotNull Long duplicateId) {}

    /**
     * What is printed on a library card. {@code barcode} is the card ID as Code 128, one character per
     * module: "1" a bar, "0" a space (the quiet zones around it are left to the page).
     */
    public record PrintableCard(String libraryName, String name, String libraryCardId, LocalDate cardExpiresOn,
                                boolean cardExpired, String barcode) {}

    /** A card ID the member no longer uses. For MERGED, {@code note} is the name on the merged record. */
    public record RetiredCardView(String libraryCardId, RetiredCard.Reason reason, String note, Instant retiredAt,
                                  String retiredBy) {
        static RetiredCardView of(RetiredCard c) {
            return new RetiredCardView(c.getLibraryCardId(), c.getReason(), c.getNote(), c.getRetiredAt(),
                    c.getRetiredBy());
        }
    }
}
