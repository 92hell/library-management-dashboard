package com.helmifr.library.controller;

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
    public List<Member> getMembers() {
        Member member = new Member();
        return memberService.searchMembers(member);
    }

    @GetMapping("/{id}")
    public Member getMember(@PathVariable long id) {
        return memberService.findMemberById(id);
    }

    @PostMapping
    public ResponseEntity<Member> createMember(@RequestBody Member member) throws URISyntaxException {
        Member saved = memberService.saveMember(member);
        return ResponseEntity.created(new URI("/clients/" + saved.getId())).body(saved);
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
