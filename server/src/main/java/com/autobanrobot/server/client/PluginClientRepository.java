package com.autobanrobot.server.client;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginClientRepository extends JpaRepository<PluginClient, Long> {

    Optional<PluginClient> findByInstallationId(String installationId);

    long countByLastSeenAtGreaterThanEqualAndClientType(Instant threshold, String clientType);

    long countByClientType(String clientType);
}
