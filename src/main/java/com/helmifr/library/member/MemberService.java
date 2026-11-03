package com.helmifr.library.member;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.account.UserAccountRepository;
import com.helmifr.library.common.Audit;
import com.helmifr.library.common.ConflictException;
import com.helmifr.library.common.NotFoundException;
import com.helmifr.library.common.PageResponse;
import com.helmifr.library.common.PhoneNumbers;
import com.helmifr.library.common.Ref;
import com.helmifr.library.common.SearchPatterns;
import com.helmifr.library.loan.LoanRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository members;
    private final LoanRepository loans;
    private final UserAccountRepository accounts;
    private final MemberCards cards;
    private final Clock clock;

    public PageResponse<MemberResponse> search(String q, Pageable pageable) {
        Page<Member> page = members.search(SearchPatterns.contains(q), pageable);
        Map<Long, String> usernames = accounts.findAllByMemberIdIn(page.map(Member::getId).getContent()).stream()
                .collect(Collectors.toMap(a -> a.getMember().getId(), UserAccount::getUsername));
        LocalDate today = LocalDate.now(clock);
        return PageResponse.of(page.map(m -> MemberResponse.from(m, usernames.get(m.getId()), today)));
    }

    public List<Ref> options() {
        return members.findAllByDeletedFalseAndActiveTrueOrderByNameAsc().stream()
                .map(m -> new Ref(m.getId(), m.getName() + " (" + m.getLibraryCardId() + ")"))
                .toList();
    }

    public MemberResponse get(Long id) {
        return toResponse(require(id));
    }

    /** Loads a member that has not been deleted; throws 404 otherwise. */
    public Member require(Long id) {
        return members.findByIdAndDeletedFalse(id).orElseThrow(() -> new NotFoundException("Member", id));
    }

    /**
     * Loads and locks a member that has not been deleted (404 otherwise). Everything that adds a
     * loan for a member takes this before any book lock; see {@link MemberMerge}.
     */
    public Member lock(Long id) {
        return members.findForUpdate(id).orElseThrow(() -> new NotFoundException("Member", id));
    }

    @Transactional
    public MemberResponse create(MemberRequest request) {
        Member member = new Member();
        cards.issue(member);
        apply(member, request);
        member = members.save(member);
        Audit.event("MEMBER_CREATED", "memberId={} code={}", member.getId(), member.getLibraryCardId());
        return toResponse(member);
    }

    @Transactional
    public MemberResponse update(Long id, MemberRequest request) {
        Member member = require(id);
        apply(member, request);
        return toResponse(member);
    }

    /** Soft-deletes the member and disables their online account, if any. */
    @Transactional
    public void delete(Long id) {
        Member member = require(id);
        if (loans.hasOpenLoans(id)) {
            throw new ConflictException("Member still has books on loan or pending requests. Resolve them first.");
        }
        member.setDeleted(true);
        accounts.findByMemberId(id).ifPresent(account -> account.setEnabled(false));
        Audit.event("MEMBER_DELETED", "memberId={} code={}", member.getId(), member.getLibraryCardId());
    }

    private MemberResponse toResponse(Member m) {
        String username = accounts.findByMemberId(m.getId()).map(UserAccount::getUsername).orElse(null);
        return MemberResponse.from(m, username, LocalDate.now(clock));
    }

    private static void apply(Member member, MemberRequest request) {
        member.setName(request.name().trim());
        member.setDateOfBirth(request.dateOfBirth());
        member.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        member.setPhone(PhoneNumbers.normalize(request.phone()));
        member.setActive(request.active() == null || request.active());
    }
}
