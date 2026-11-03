package com.helmifr.library.loan;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanEventRepository extends JpaRepository<LoanEvent, Long> {
    List<LoanEvent> findAllByLoanIdOrderByIdAsc(Long loanId);
}
