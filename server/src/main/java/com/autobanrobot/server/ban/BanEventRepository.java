package com.autobanrobot.server.ban;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BanEventRepository extends JpaRepository<BanEvent, Long> {

    interface InstallationContribution {
        String getInstallationId();
        long getBanCount();
    }

    Optional<BanEvent> findByClientEventId(String clientEventId);
    List<BanEvent> findByInstallationIdAndAccountIdIsNull(String installationId);

    Optional<BanEvent> findTopByUsernameIgnoreCaseAndPageUrlAndContentAndBlockedAtGreaterThanEqualOrderByBlockedAtDesc(
        String username,
        String pageUrl,
        String content,
        Instant blockedAt
    );

    Page<BanEvent> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

    long countByBlockedAtGreaterThanEqual(Instant blockedAt);

    @Query("""
        select event.installationId as installationId, count(event.id) as banCount
        from BanEvent event
        where event.installationId is not null and event.installationId <> ''
        group by event.installationId
        """)
    List<InstallationContribution> countContributionsByInstallationId();
}
