package com.autobanrobot.server.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountSessionRepository extends JpaRepository<AccountSession, Long> {
    Optional<AccountSession> findByTokenHash(String tokenHash);
    void deleteByTokenHash(String tokenHash);
    void deleteByAccountId(Long accountId);
}
