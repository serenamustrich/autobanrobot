package com.autobanrobot.server.client;

import java.time.Duration;
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

    @Column(name = "device_name", nullable = false, length = 128, columnDefinition = "varchar(128) not null default ''")
    private String deviceName;

    @Column(name = "plugin_version", nullable = false, length = 32)
    private String pluginVersion;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "online_seconds", nullable = false, columnDefinition = "bigint not null default 0")
    private long onlineSeconds;

    @Column(name = "location_label", length = 160)
    private String locationLabel;

    @Column(name = "location_latitude")
    private Double locationLatitude;

    @Column(name = "location_longitude")
    private Double locationLongitude;

    @Column(name = "location_updated_at")
    private Instant locationUpdatedAt;

    protected PluginClient() {
    }

    public PluginClient(
        String installationId,
        String platform,
        String pluginVersion,
        Instant firstSeenAt,
        Instant lastSeenAt
    ) {
        this(installationId, "plugin", platform, "", pluginVersion, firstSeenAt, lastSeenAt);
    }

    public PluginClient(
        String installationId,
        String clientType,
        String platform,
        String pluginVersion,
        Instant firstSeenAt,
        Instant lastSeenAt
    ) {
        this(installationId, clientType, platform, "", pluginVersion, firstSeenAt, lastSeenAt);
    }

    public PluginClient(
        String installationId,
        String clientType,
        String platform,
        String deviceName,
        String pluginVersion,
        Instant firstSeenAt,
        Instant lastSeenAt
    ) {
        this.installationId = installationId;
        this.clientType = clientType;
        this.platform = platform;
        this.deviceName = deviceName;
        this.pluginVersion = pluginVersion;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.onlineSeconds = 0;
    }

    public void markSeen(String platform, String pluginVersion, Instant seenAt) {
        markSeen("plugin", platform, "", pluginVersion, seenAt, IpLocation.unresolved());
    }

    public void markSeen(
        String clientType,
        String platform,
        String deviceName,
        String pluginVersion,
        Instant seenAt,
        IpLocation location
    ) {
        long elapsed = Duration.between(this.lastSeenAt, seenAt).toSeconds();
        if (elapsed > 0 && elapsed <= 120) {
            onlineSeconds = Math.min(Long.MAX_VALUE - elapsed, onlineSeconds) + elapsed;
        }
        this.clientType = clientType;
        this.platform = platform;
        this.deviceName = deviceName;
        this.pluginVersion = pluginVersion;
        this.lastSeenAt = seenAt;
        if (location.isResolved()) {
            this.locationLabel = location.label();
            this.locationLatitude = location.latitude();
            this.locationLongitude = location.longitude();
            this.locationUpdatedAt = seenAt;
        }
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

    public String getDeviceName() {
        return deviceName;
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

    public long getOnlineSeconds() {
        return onlineSeconds;
    }

    public String getLocationLabel() { return locationLabel; }
    public Double getLocationLatitude() { return locationLatitude; }
    public Double getLocationLongitude() { return locationLongitude; }
}
