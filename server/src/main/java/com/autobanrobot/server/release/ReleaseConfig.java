package com.autobanrobot.server.release;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "release_config")
public class ReleaseConfig {

    @Id
    private Long id;

    @Column(name = "release_tag", nullable = false, length = 64)
    private String tag;

    @Column(name = "release_url", nullable = false, length = 512)
    private String releaseUrl;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReleaseConfig() {
    }

    public ReleaseConfig(Long id, String tag, String releaseUrl, Instant updatedAt) {
        this.id = id;
        this.tag = tag;
        this.releaseUrl = releaseUrl;
        this.updatedAt = updatedAt;
    }

    public String getTag() { return tag; }
    public String getReleaseUrl() { return releaseUrl; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String tag, String releaseUrl, Instant updatedAt) {
        this.tag = tag;
        this.releaseUrl = releaseUrl;
        this.updatedAt = updatedAt;
    }
}
