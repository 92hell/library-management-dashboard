package com.helmifr.library.service;

import com.helmifr.library.dao.MemberDAO;
import com.helmifr.library.entity.Member;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MemberService {
    @Autowired
    MemberDAO memberDAO;

    public Member findMemberById(Long id) {
        Optional<Member> member = memberDAO.findById(id);
        return member.orElse(null);
    }

    public List<Member> findAllMembers() {
        return memberDAO.findAllByIsDeletedFalse();
    }

    public List<Member> searchMembers(Member member) {
        return memberDAO.findAll(Example.of(member,
                ExampleMatcher.matching()
                        .withIgnoreCase()
                        .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
        ), Sort.by(Sort.Order.desc("created_at")));
    }

    public Member saveMember(Member member) {
        return memberDAO.save(member);
    }

    public void deleteMemberById(Long id) {
        Member member = findMemberById(id);
        if(member != null) {
            member.setIsDeleted(true);
            memberDAO.save(member);
        }
    }
}
