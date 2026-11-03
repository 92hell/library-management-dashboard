package com.helmifr.library.member;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetiredCardRepository extends JpaRepository<RetiredCard, Long> {

    List<RetiredCard> findAllByMemberIdOrderByIdDesc(Long memberId);

    boolean existsByLibraryCardIdIgnoreCase(String libraryCardId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RetiredCard c SET c.memberId = :to WHERE c.memberId = :from")
    int moveAll(@Param("from") Long from, @Param("to") Long to);
}
