package com.autobanrobot.server.client;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "plugin_client",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_plugin_client_installation_id",
        columnNames = "installation_id"
    )
)
public class PluginClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "installation_id", nullable = false, length = 64)
    private String installationId;

    @Column(name = "client_type", nullable = false, length = 16, columnDefinition = "varchar(16) default 'plugin'")
    private String clientType;

    @Column(nullable = false, length = 32)
    private String platform;

    @Column(name = "plugin_version", nullable = false, length = 32)
    private String pluginVersion;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected PluginClient() {
    }

    public PluginClient(
        String installationId,
        String platform,
        String pluginVersion,
        Instant firstSeenAt,
        Instant lastSeenAt
    ) {
        this(installationId, "plugin", platform, pluginVersion, firstSeenAt, lastSeenAt);
    }

    public PluginClient(
        String installationId,
        String clientType,
        String platform,
        String pluginVersion,
        Instant firstSeenAt,
        Instant lastSeenAt
    ) {
        this.installationId = installationId;
        this.clientType = clientType;
        this.platform = platform;
        this.pluginVersion = pluginVersion;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
    }

    public void markSeen(String platform, String pluginVersion, Instant seenAt) {
        markSeen("plugin", platform, pluginVersion, seenAt);
    }

    public void markSeen(String clientType, String platform, String pluginVersion, Instant seenAt) {
        this.clientType = clientType;
        this.platform = platform;
        this.pluginVersion = pluginVersion;
        this.lastSeenAt = seenAt;
    }

    public Long getId() {
        return id;
    }

    public String getInstallationId() {
        return installationId;
    }

    public String getClientType() {
        return clientType;
    }

    public String getPlatform() {
        return platform;
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
