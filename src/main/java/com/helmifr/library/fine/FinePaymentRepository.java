package com.helmifr.library.fine;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinePaymentRepository extends JpaRepository<FinePayment, Long> {

    List<FinePayment> findAllByFineIdOrderByIdAsc(Long fineId);

    /** Money taken in a period (fines of deleted loans left out), for reports. */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM FinePayment p WHERE p.fine.loan.deleted = false"
            + " AND p.paidAt >= :from AND p.paidAt < :to")
    long sumPaidBetween(@Param("from") Instant from, @Param("to") Instant to);
}
