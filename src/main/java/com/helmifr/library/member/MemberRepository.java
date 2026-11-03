package com.helmifr.library.member;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByIdAndDeletedFalse(Long id);

    /**
     * Locks the member row. Taken before any book lock by everything that adds loans for a member
     * or moves them (merging), so a loan cannot land on a record that is being merged away.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :id AND m.deleted = false")
    Optional<Member> findForUpdate(@Param("id") Long id);

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
                   OR LOWER(m.libraryCardId) LIKE :pattern ESCAPE '!'
                   OR EXISTS (SELECT 1 FROM RetiredCard c
                              WHERE c.memberId = m.id AND LOWER(c.libraryCardId) LIKE :pattern ESCAPE '!'))
            ORDER BY m.createdAt DESC, m.id DESC""")
    Page<Member> search(@Param("pattern") String pattern, Pageable pageable);
}
