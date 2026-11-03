package com.helmifr.library.receipt;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    Optional<Receipt> findFirstByLoanIdOrderByIdDesc(Long loanId);

    @EntityGraph(attributePaths = {"loan", "loan.member"})
    Optional<Receipt> findByNumber(String number);
}
