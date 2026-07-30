package com.autobanrobot.server.client;

import java.time.Duration;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PluginClientService {

    private static final Duration ONLINE_WINDOW = Duration.ofMinutes(2);

    private final PluginClientRepository repository;

    public PluginClientService(PluginClientRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void heartbeat(PluginHeartbeatRequest request) {
        Instant now = Instant.now();
        String installationId = request.installationId().trim();
        String platform = request.platform().trim();
        String version = request.version().trim();

        var existing = repository.findByInstallationId(installationId);
        if (existing.isPresent()) {
            existing.get().markSeen(platform, version, now);
            return;
        }

        try {
            repository.saveAndFlush(new PluginClient(
                installationId,
                platform,
                version,
                now,
                now
            ));
        } catch (DataIntegrityViolationException duplicate) {
            repository.findByInstallationId(installationId).ifPresent(client -> {
                client.markSeen(platform, version, now);
                repository.save(client);
            });
        }
    }

    @Transactional(readOnly = true)
    public PluginUserStatsResponse stats() {
        Instant threshold = Instant.now().minus(ONLINE_WINDOW);
        return new PluginUserStatsResponse(
            repository.countByLastSeenAtGreaterThanEqual(threshold),
            repository.count(),
            ONLINE_WINDOW.toSeconds()
        );
    }
}
