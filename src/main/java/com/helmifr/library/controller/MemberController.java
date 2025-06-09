package com.helmifr.library.controller;

import com.helmifr.library.entity.Book; // Import Book for borrowed books endpoint
import com.helmifr.library.entity.Member;
import com.helmifr.library.service.MemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@RestController
@RequestMapping("/members")
public class MemberController {
    @Autowired
    MemberService memberService;

    @GetMapping
    public List<Member> getMembers(@RequestParam(required = false) String searchTerm) {
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            return memberService.searchMembers(searchTerm);
        } else {
            return memberService.findAllNonDeletedMembers();
        }
    }

    @GetMapping("/{id}")
    public Member getMember(@PathVariable long id) {
        return memberService.findMemberById(id);
    }

    @GetMapping("/{memberId}/borrowedBooks")
    public ResponseEntity<List<Book>> getBorrowedBooksByMember(@PathVariable Long memberId) {
        List<Book> books = memberService.getNonDeletedBorrowedBooksByMemberId(memberId);
        if (books.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(books);
    }

    @PostMapping
    public ResponseEntity<Member> createMember(@RequestBody Member member) throws URISyntaxException {
        Member saved = memberService.saveMember(member);
        return ResponseEntity.created(new URI("/members/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Member> updateMember(@PathVariable long id, @RequestBody Member member) {
        if(memberService.findMemberById(id) != null) {
            Member updated = memberService.saveMember(member);
            return ResponseEntity.ok(updated);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Member> deleteMember(@PathVariable long id) {
        memberService.deleteMemberById(id);
        return ResponseEntity.ok().build();
    }
}