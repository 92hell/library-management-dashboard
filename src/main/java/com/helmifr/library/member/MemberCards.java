package com.helmifr.library.member;

import com.helmifr.library.common.Audit;
import com.helmifr.library.common.NotFoundException;
import com.helmifr.library.member.MemberCardDtos.RetiredCardView;
import com.helmifr.library.security.CurrentUser;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issuing, renewing and replacing library cards, and the history of card IDs a member has had. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberCards {

    private final MemberRepository members;
    private final RetiredCardRepository retiredCards;
    private final LibraryCardIds libraryCardIds;
    private final MemberCardPolicy policy;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Gives a new member record its card: a fresh ID, valid for the configured period from today. */
    public void issue(Member member) {
        member.setLibraryCardId(libraryCardIds.next());
        member.setCardExpiresOn(policy.expiryFrom(today()));
    }

    /** Extends the card a full period: from today if it has expired, else from its current expiry. */
    @Transactional
    public void renew(Long memberId) {
        Member member = lock(memberId);
        boolean wasExpired = member.cardExpiredOn(today());
        LocalDate before = member.getCardExpiresOn();
        member.setCardExpiresOn(policy.renewed(before, today()));
        members.flush();
        Audit.event("MEMBER_CARD_RENEWED", "memberId={} code={} from={} to={} by={}", member.getId(),
                member.getLibraryCardId(), before, member.getCardExpiresOn(), CurrentUser.username());
        if (wasExpired) {
            events.publishEvent(new CardRenewed(member.getId()));
        }
    }

    /** A lost or damaged card: the member gets a new ID; the old one is retired with the reason. */
    @Transactional
    public void replace(Long memberId, String reason) {
        Member member = lock(memberId);
        String old = member.getLibraryCardId();
        retire(old, member.getId(), RetiredCard.Reason.REPLACED, reason.trim());
        member.setLibraryCardId(libraryCardIds.next());
        Audit.event("MEMBER_CARD_REPLACED", "memberId={} old={} new={} by={}", member.getId(), old,
                member.getLibraryCardId(), CurrentUser.username());
    }

    public List<RetiredCardView> history(Long memberId) {
        return retiredCards.findAllByMemberIdOrderByIdDesc(memberId).stream().map(RetiredCardView::of).toList();
    }

    void retire(String libraryCardId, Long memberId, RetiredCard.Reason reason, String note) {
        RetiredCard card = new RetiredCard();
        card.setLibraryCardId(libraryCardId);
        card.setMemberId(memberId);
        card.setReason(reason);
        card.setNote(note);
        card.setRetiredAt(clock.instant());
        card.setRetiredBy(CurrentUser.username());
        retiredCards.save(card);
    }

    private Member lock(Long memberId) {
        return members.findForUpdate(memberId)
                .orElseThrow(() -> new NotFoundException("Member", memberId));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
