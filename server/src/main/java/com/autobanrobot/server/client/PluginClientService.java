package com.autobanrobot.server.client;

import java.time.Duration;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PluginClientService {

    private static final Duration ONLINE_WINDOW = Duration.ofMinutes(2);
    private static final String PLUGIN = "plugin";
    private static final String APP = "app";

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
        String clientType = normalizeClientType(request.clientType());

        var existing = repository.findByInstallationId(installationId);
        if (existing.isPresent()) {
            existing.get().markSeen(clientType, platform, version, now);
            return;
        }

        try {
            repository.saveAndFlush(new PluginClient(
                installationId,
                clientType,
                platform,
                version,
                now,
                now
            ));
        } catch (DataIntegrityViolationException duplicate) {
            repository.findByInstallationId(installationId).ifPresent(client -> {
                client.markSeen(clientType, platform, version, now);
                repository.save(client);
            });
        }
    }

    @Transactional(readOnly = true)
    public ClientStatsResponse stats() {
        Instant threshold = Instant.now().minus(ONLINE_WINDOW);
        return new ClientStatsResponse(
            statsFor(PLUGIN, threshold),
            statsFor(APP, threshold)
        );
    }

    private ClientUserStatsResponse statsFor(String clientType, Instant threshold) {
        return new ClientUserStatsResponse(
            repository.countByLastSeenAtGreaterThanEqualAndClientType(threshold, clientType),
            repository.countByClientType(clientType),
            ONLINE_WINDOW.toSeconds()
        );
    }

    private String normalizeClientType(String value) {
        return APP.equalsIgnoreCase(value == null ? "" : value.trim()) ? APP : PLUGIN;
    }
}
