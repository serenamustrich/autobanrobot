package com.autobanrobot.server.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "autoban_account_settings")
public class AccountSettings {

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String keywords;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String whitelist;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "settings_revision", nullable = false)
    private long settingsRevision;

    protected AccountSettings() {
    }

    public AccountSettings(Long accountId, String keywords, String whitelist, Instant updatedAt) {
        this.accountId = accountId;
        this.keywords = keywords;
        this.whitelist = whitelist;
        this.updatedAt = updatedAt;
        this.settingsRevision = 0;
    }

    public String getKeywords() { return keywords; }
    public String getWhitelist() { return whitelist; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getSettingsRevision() { return settingsRevision; }

    public void replace(String keywords, String whitelist, Instant updatedAt) {
        this.keywords = keywords;
        this.whitelist = whitelist;
        this.updatedAt = updatedAt;
        this.settingsRevision++;
    }
}
