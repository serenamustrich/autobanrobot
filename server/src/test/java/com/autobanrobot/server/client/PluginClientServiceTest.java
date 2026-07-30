package com.autobanrobot.server.client;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginClientServiceTest {

    @Test
    void updatesAnExistingAnonymousInstallation() {
        PluginClientRepository repository = mock(PluginClientRepository.class);
        PluginClient client = new PluginClient(
            "installation-1",
            "chrome-edge",
            "1.6.0",
            Instant.parse("2026-07-30T00:00:00Z"),
            Instant.parse("2026-07-30T00:00:00Z")
        );
        when(repository.findByInstallationId("installation-1"))
            .thenReturn(Optional.of(client));

        new PluginClientService(repository).heartbeat(new PluginHeartbeatRequest(
            "installation-1",
            "chrome-edge",
            "1.6.1"
        ));

        assertEquals("1.6.1", client.getPluginVersion());
        verify(repository).findByInstallationId("installation-1");
    }

    @Test
    void reportsOnlineAndCumulativeUsers() {
        PluginClientRepository repository = mock(PluginClientRepository.class);
        when(repository.countByLastSeenAtGreaterThanEqual(any())).thenReturn(3L);
        when(repository.count()).thenReturn(8L);

        PluginUserStatsResponse stats =
            new PluginClientService(repository).stats();

        assertEquals(3, stats.onlineUsers());
        assertEquals(8, stats.cumulativeUsers());
        assertEquals(120, stats.onlineWindowSeconds());
    }
}
