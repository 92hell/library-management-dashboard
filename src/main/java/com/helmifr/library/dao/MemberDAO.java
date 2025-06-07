package com.helmifr.library.dao;

import com.helmifr.library.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberDAO extends JpaRepository<Member, Long> {
    List<Member> findAllByIsDeletedFalse();
}
