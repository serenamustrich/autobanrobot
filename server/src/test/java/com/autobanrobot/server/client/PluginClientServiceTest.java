package com.autobanrobot.server.client;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginClientServiceTest {

    @Test
    void updatesAnExistingAnonymousInstallation() {
        PluginClientRepository repository = mock(PluginClientRepository.class);
        PluginClient client = new PluginClient(
            "installation-1",
            "plugin",
            "chrome-edge",
            "1.6.0",
            Instant.parse("2026-07-30T00:00:00Z"),
            Instant.parse("2026-07-30T00:00:00Z")
        );
        when(repository.findByInstallationId("installation-1"))
            .thenReturn(Optional.of(client));
        IpGeolocationService geolocation = mock(IpGeolocationService.class);
        when(geolocation.locate("")).thenReturn(IpLocation.unresolved());

        new PluginClientService(repository, geolocation).heartbeat(new PluginHeartbeatRequest(
            "installation-1",
            "chrome-edge",
            "1.6.1",
            "plugin",
            "Chrome on macOS"
        ));

        assertEquals("1.6.1", client.getPluginVersion());
        assertEquals("Chrome on macOS", client.getDeviceName());
        verify(repository).findByInstallationId("installation-1");
    }

    @Test
    void reportsOnlineAndCumulativeUsers() {
        PluginClientRepository repository = mock(PluginClientRepository.class);
        when(repository.countByLastSeenAtGreaterThanEqualAndClientType(any(), eq("plugin"))).thenReturn(3L);
        when(repository.countByLastSeenAtGreaterThanEqualAndClientType(any(), eq("app"))).thenReturn(1L);
        when(repository.countByClientType("plugin")).thenReturn(8L);
        when(repository.countByClientType("app")).thenReturn(2L);
        IpGeolocationService geolocation = mock(IpGeolocationService.class);

        ClientStatsResponse stats =
            new PluginClientService(repository, geolocation).stats();

        assertEquals(3, stats.plugin().onlineUsers());
        assertEquals(8, stats.plugin().cumulativeUsers());
        assertEquals(1, stats.app().onlineUsers());
        assertEquals(2, stats.app().cumulativeUsers());
        assertEquals(120, stats.plugin().onlineWindowSeconds());
    }
}
