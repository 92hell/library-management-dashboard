package com.helmifr.library.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class MemberCardDtos {
    private MemberCardDtos() {}

    /** Why the card is replaced, e.g. "Lost" (kept in the card history). */
    public record ReplaceCardRequest(@NotBlank @Size(max = 500) String reason) {}

    /** The duplicate record whose loans, fines and account move to the member kept. */
    public record MergeRequest(@NotNull Long duplicateId) {}

    /** A card ID the member no longer uses. For MERGED, {@code note} is the name on the merged record. */
    public record RetiredCardView(String libraryCardId, RetiredCard.Reason reason, String note, Instant retiredAt,
                                  String retiredBy) {
        static RetiredCardView of(RetiredCard c) {
            return new RetiredCardView(c.getLibraryCardId(), c.getReason(), c.getNote(), c.getRetiredAt(),
                    c.getRetiredBy());
        }
    }
}
