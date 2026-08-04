package com.autobanrobot.server.client;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PluginClientRepository extends JpaRepository<PluginClient, Long> {

    Optional<PluginClient> findByInstallationId(String installationId);

    long countByLastSeenAtGreaterThanEqualAndClientType(Instant threshold, String clientType);

    long countByClientType(String clientType);

    List<PluginClient> findTop500ByOrderByLastSeenAtDesc();

    @Query("select coalesce(sum(client.onlineSeconds), 0) from PluginClient client")
    Long sumOnlineSeconds();
}
