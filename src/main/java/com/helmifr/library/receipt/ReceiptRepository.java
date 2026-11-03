package com.helmifr.library.receipt;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    Optional<Receipt> findFirstByLoanIdOrderByIdDesc(Long loanId);

    @EntityGraph(attributePaths = {"loan", "loan.member"})
    Optional<Receipt> findByNumber(String number);

    /** Merging duplicate members: the kept member (and their account) may open the duplicate's receipts. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Receipt r SET r.memberId = :to WHERE r.memberId = :from")
    int moveAllToMember(@Param("from") Long from, @Param("to") Long to);
}
