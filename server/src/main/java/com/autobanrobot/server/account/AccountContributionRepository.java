package com.autobanrobot.server.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AccountContributionRepository extends JpaRepository<AccountContribution, Long> {
    boolean existsByAccountIdAndTargetUsername(Long accountId, String targetUsername);
    long countByAccountId(Long accountId);

    /** The unique index is the concurrency boundary for cross-device contribution credit. */
    @Modifying
    @Query(value = """
        insert ignore into autoban_account_contribution
          (account_id, target_username, first_ban_event_id, created_at)
        values (:accountId, :targetUsername, :eventId, :createdAt)
        """, nativeQuery = true)
    int insertIgnore(
        @Param("accountId") Long accountId,
        @Param("targetUsername") String targetUsername,
        @Param("eventId") Long eventId,
        @Param("createdAt") Instant createdAt
    );
}
