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
@Table(name = "autoban_account_device", indexes = @Index(name = "idx_autoban_account_device_account", columnList = "account_id"), uniqueConstraints = @UniqueConstraint(name = "uniq_autoban_account_device_installation", columnNames = "installation_id"))
public class AccountDevice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "account_id", nullable = false) private Long accountId;
    @Column(name = "installation_id", nullable = false, length = 64) private String installationId;
    @Column(name = "bound_at", nullable = false) private Instant boundAt;
    protected AccountDevice() { }
    public AccountDevice(Long accountId, String installationId, Instant boundAt) { this.accountId = accountId; this.installationId = installationId; this.boundAt = boundAt; }
    public Long getAccountId() { return accountId; }
    public String getInstallationId() { return installationId; }
    public void bindTo(Long accountId, Instant boundAt) { this.accountId = accountId; this.boundAt = boundAt; }
}
