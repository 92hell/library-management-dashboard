package com.helmifr.library.service;

import com.helmifr.library.dao.MemberDAO;
import com.helmifr.library.entity.Book;
import com.helmifr.library.entity.Member;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemberService {
    @Autowired
    MemberDAO memberDAO;

    public Member findMemberById(Long id) {
        return memberDAO.findByIdAndIsDeletedFalse(id).orElse(null);
    }

    public List<Member> findAllNonDeletedMembers() {
        return memberDAO.findAllByIsDeletedFalseOrderByCreatedAtDesc();
    }

    public List<Member> searchMembers(String searchTerm) {
        String actualSearchTerm = (searchTerm != null && !searchTerm.trim().isEmpty()) ? searchTerm.trim() : null;
        return memberDAO.searchMembersByCriteria(actualSearchTerm);
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

    public List<Book> getNonDeletedBorrowedBooksByMemberId(Long memberId) {
        return memberDAO.findNonDeletedBorrowedBooksByMemberId(memberId);
    }
}