package com.helmifr.library.account;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    @EntityGraph(attributePaths = "member")
    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    /** Accounts with this username, or this email on the account or on its library card ("forgot password"). */
    @Query("""
            SELECT a FROM UserAccount a LEFT JOIN a.member m
            WHERE LOWER(a.username) = LOWER(:id) OR LOWER(a.email) = LOWER(:id) OR LOWER(m.email) = LOWER(:id)""")
    List<UserAccount> findByUsernameOrEmail(@Param("id") String identifier);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByRole(Role role);

    /** Demo reset: removes every account except the owner's. */
    @Modifying
    @Query("DELETE FROM UserAccount u WHERE LOWER(u.username) <> LOWER(:keep)")
    int deleteAllExcept(@Param("keep") String username);

    Optional<UserAccount> findByMemberId(Long memberId);

    List<UserAccount> findAllByMemberIdIn(Collection<Long> memberIds);

    List<UserAccount> findAllByRoleAndEnabledTrue(Role role);

    /** @param pattern from {@link com.helmifr.library.common.SearchPatterns}, or null for all */
    @Query("""
            SELECT u FROM UserAccount u
            WHERE (:role IS NULL OR u.role = :role)
              AND (:pattern IS NULL
                   OR LOWER(u.username) LIKE :pattern ESCAPE '!'
                   OR LOWER(u.displayName) LIKE :pattern ESCAPE '!'
                   OR LOWER(u.email) LIKE :pattern ESCAPE '!')
            ORDER BY u.createdAt DESC, u.id DESC""")
    Page<UserAccount> search(@Param("role") Role role, @Param("pattern") String pattern, Pageable pageable);
}
