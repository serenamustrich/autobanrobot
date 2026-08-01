package com.autobanrobot.server.ban;

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
@Table(
    name = "ban_event",
    indexes = {
        @Index(name = "idx_ban_event_blocked_at", columnList = "blocked_at"),
        @Index(name = "idx_ban_event_username", columnList = "username")
    },
    uniqueConstraints = @UniqueConstraint(
        name = "uk_ban_event_client_event_id",
        columnNames = "client_event_id"
    )
)
public class BanEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_event_id", nullable = false, length = 64)
    private String clientEventId;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "matched_keywords", nullable = false, length = 1000)
    private String matchedKeywords;

    @Column(
        name = "configured_keywords",
        nullable = false,
        columnDefinition = "MEDIUMTEXT"
    )
    private String configuredKeywords;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "page_url", nullable = false, length = 1000)
    private String pageUrl;

    @Column(name = "blocked_at", nullable = false)
    private Instant blockedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected BanEvent() {
    }

    public BanEvent(
        String clientEventId,
        String username,
        String displayName,
        String reason,
        String matchedKeywords,
        String configuredKeywords,
        String content,
        String pageUrl,
        Instant blockedAt,
        Instant receivedAt
    ) {
        this.clientEventId = clientEventId;
        this.username = username;
        this.displayName = displayName;
        this.reason = reason;
        this.matchedKeywords = matchedKeywords;
        this.configuredKeywords = configuredKeywords;
        this.content = content;
        this.pageUrl = pageUrl;
        this.blockedAt = blockedAt;
        this.receivedAt = receivedAt;
    }

    public Long getId() {
        return id;
    }

    public String getClientEventId() {
        return clientEventId;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getReason() {
        return reason;
    }

    public String getMatchedKeywords() {
        return matchedKeywords;
    }

    public String getConfiguredKeywords() {
        return configuredKeywords;
    }

    public String getContent() {
        return content;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public Instant getBlockedAt() {
        return blockedAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
