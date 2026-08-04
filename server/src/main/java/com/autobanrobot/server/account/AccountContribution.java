package com.autobanrobot.server.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "autoban_account_contribution", indexes = @Index(name = "idx_autoban_account_contribution_account", columnList = "account_id"), uniqueConstraints = @UniqueConstraint(name = "uniq_autoban_account_contribution_target", columnNames = {"account_id", "target_username"}))
public class AccountContribution {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "account_id", nullable = false) private Long accountId;
    @Column(name = "target_username", nullable = false, length = 64) private String targetUsername;
    @Column(name = "first_ban_event_id") private Long firstBanEventId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected AccountContribution() { }
    public AccountContribution(Long accountId, String targetUsername, Long firstBanEventId, Instant createdAt) { this.accountId = accountId; this.targetUsername = targetUsername; this.firstBanEventId = firstBanEventId; this.createdAt = createdAt; }
}
