package com.helmifr.library.member;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByIdAndDeletedFalse(Long id);

    Optional<Member> findByLibraryCardIdIgnoreCaseAndDeletedFalse(String libraryCardId);

    boolean existsByLibraryCardId(String libraryCardId);

    /** Cards without an online account whose email matches, or whose phone contains the given digits. */
    @Query("""
            SELECT m FROM Member m
            WHERE m.deleted = false
              AND NOT EXISTS (SELECT 1 FROM UserAccount u WHERE u.member = m)
              AND (LOWER(m.email) = LOWER(:email) OR m.phone LIKE CONCAT('%', :phoneDigits, '%'))""")
    List<Member> findUnlinkedByEmailOrPhoneContaining(@Param("email") String email, @Param("phoneDigits") String phoneDigits);

    long countByDeletedFalse();

    List<Member> findAllByDeletedFalseAndActiveTrueOrderByNameAsc();

    /** @param pattern from {@link com.helmifr.library.common.SearchPatterns}, or null for all */
    @Query("""
            SELECT m FROM Member m
            WHERE m.deleted = false
              AND (:pattern IS NULL
                   OR LOWER(m.name) LIKE :pattern ESCAPE '!'
                   OR LOWER(m.email) LIKE :pattern ESCAPE '!'
                   OR LOWER(m.phone) LIKE :pattern ESCAPE '!'
                   OR LOWER(m.libraryCardId) LIKE :pattern ESCAPE '!')
            ORDER BY m.createdAt DESC, m.id DESC""")
    Page<Member> search(@Param("pattern") String pattern, Pageable pageable);
}
