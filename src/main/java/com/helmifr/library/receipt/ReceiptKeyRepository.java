package com.helmifr.library.receipt;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptKeyRepository extends JpaRepository<ReceiptKey, Long> {
    Optional<ReceiptKey> findFirstByOrderByIdDesc();

    Optional<ReceiptKey> findByKeyId(String keyId);
}
