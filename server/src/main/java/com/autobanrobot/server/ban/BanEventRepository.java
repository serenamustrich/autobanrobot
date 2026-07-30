package com.autobanrobot.server.ban;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface BanEventRepository extends JpaRepository<BanEvent, Long> {

    Optional<BanEvent> findByClientEventId(String clientEventId);

    Page<BanEvent> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

    long countByBlockedAtGreaterThanEqual(Instant blockedAt);
}
