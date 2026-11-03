package com.helmifr.library.member;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.account.UserAccountRepository;
import com.helmifr.library.book.BookRepository;
import com.helmifr.library.common.Audit;
import com.helmifr.library.common.ConflictException;
import com.helmifr.library.common.NotFoundException;
import com.helmifr.library.fine.FineRepository;
import com.helmifr.library.loan.LoanRepository;
import com.helmifr.library.receipt.ReceiptRepository;
import com.helmifr.library.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merges a duplicate member record into the one kept (e.g. someone registered online without
 * their walk-in card): loans, fines, receipts, card history and the online account move over; the
 * duplicate is removed and its card ID retired as MERGED, so it still finds the member kept.
 *
 * <p>Locks, in the order every other writer takes them: both member rows (lower id first), then
 * the book of every loan that moves. Requests and desk loans lock the member first too, so none
 * can land on the duplicate mid-merge; desk actions on a moving loan wait on its book and then
 * re-read the loan with its new member.
 */
@Service
@RequiredArgsConstructor
public class MemberMerge {

    private final MemberRepository members;
    private final UserAccountRepository accounts;
    private final LoanRepository loans;
    private final FineRepository fines;
    private final ReceiptRepository receipts;
    private final BookRepository books;
    private final RetiredCardRepository retiredCards;
    private final MemberCards cards;

    @Transactional
    public void merge(Long keepId, Long duplicateId) {
        if (keepId.equals(duplicateId)) {
            throw new ConflictException("A member cannot be merged with itself.");
        }
        Member first = lock(Math.min(keepId, duplicateId));
        Member second = lock(Math.max(keepId, duplicateId));
        Member keep = first.getId().equals(keepId) ? first : second;
        Member duplicate = keep == first ? second : first;

        Optional<UserAccount> keepAccount = accounts.findByMemberId(keepId);
        Optional<UserAccount> duplicateAccount = accounts.findByMemberId(duplicateId);
        if (keepAccount.isPresent() && duplicateAccount.isPresent()) {
            throw new ConflictException("Both members have an online account, so they cannot be merged.");
        }
        List<String> both = loans.findTitlesOpenForBoth(keepId, duplicateId);
        if (!both.isEmpty()) {
            throw new ConflictException(
                    "Both members have an open request or loan for \"{0}\". Cancel or finish one of them first.",
                    both.get(0));
        }
        loans.findBookIdsOfMember(duplicateId).stream().sorted().forEach(books::findForUpdate);

        String duplicateCard = duplicate.getLibraryCardId();
        keep.setCardExpiresOn(later(keep.getCardExpiresOn(), duplicate.getCardExpiresOn()));
        duplicate.setDeleted(true);
        duplicateAccount.ifPresent(account -> account.setMember(keep));

        // The bulk moves flush the changes above first, then clear the persistence context.
        int movedLoans = loans.moveAllToMember(duplicateId, keepId);
        int movedFines = fines.moveAllToMember(duplicateId, keepId);
        receipts.moveAllToMember(duplicateId, keepId);
        retiredCards.moveAll(duplicateId, keepId);
        cards.retire(duplicateCard, keepId, RetiredCard.Reason.MERGED, duplicate.getName());

        Audit.event("MEMBERS_MERGED", "keptId={} duplicateId={} duplicateCard={} loans={} fines={} account={} by={}",
                keepId, duplicateId, duplicateCard, movedLoans, movedFines,
                duplicateAccount.map(UserAccount::getUsername).orElse("-"), CurrentUser.username());
    }

    /** The later expiry; null (does not expire) beats any date. */
    private static LocalDate later(LocalDate a, LocalDate b) {
        if (a == null || b == null) {
            return null;
        }
        return a.isAfter(b) ? a : b;
    }

    private Member lock(Long id) {
        return members.findForUpdate(id).orElseThrow(() -> new NotFoundException("Member", id));
    }
}
