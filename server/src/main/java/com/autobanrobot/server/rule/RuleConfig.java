package com.autobanrobot.server.rule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "rule_config")
public class RuleConfig {

    @Id
    private Long id;

    @Column(name = "config_version", nullable = false)
    private Long version;

    @Column(name = "rules_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String rulesJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RuleConfig() {
    }

    public RuleConfig(Long id, Long version, String rulesJson, Instant updatedAt) {
        this.id = id;
        this.version = version;
        this.rulesJson = rulesJson;
        this.updatedAt = updatedAt;
    }

    public Long getVersion() { return version; }
    public String getRulesJson() { return rulesJson; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(Long version, String rulesJson, Instant updatedAt) {
        this.version = version;
        this.rulesJson = rulesJson;
        this.updatedAt = updatedAt;
    }
}
