package com.helmifr.library.member;

import com.helmifr.library.common.PageResponse;
import com.helmifr.library.common.Ref;
import com.helmifr.library.loan.LoanResponse;
import com.helmifr.library.loan.LoanService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final LoanService loanService;

    @GetMapping
    public PageResponse<MemberResponse> list(@RequestParam(required = false) String q,
                                             @PageableDefault(size = 20) Pageable pageable) {
        return memberService.search(q, pageable);
    }

    @GetMapping("/options")
    public List<Ref> options() {
        return memberService.options();
    }

    @GetMapping("/{id}")
    public MemberResponse get(@PathVariable Long id) {
        return memberService.get(id);
    }

    @GetMapping("/{id}/loans")
    public List<LoanResponse> loans(@PathVariable Long id) {
        memberService.require(id);
        return loanService.findByMember(id);
    }

    @PostMapping
    public ResponseEntity<MemberResponse> create(@Valid @RequestBody MemberRequest request) {
        MemberResponse created = memberService.create(request);
        return ResponseEntity.created(URI.create("/api/members/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public MemberResponse update(@PathVariable Long id, @Valid @RequestBody MemberRequest request) {
        return memberService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        memberService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
