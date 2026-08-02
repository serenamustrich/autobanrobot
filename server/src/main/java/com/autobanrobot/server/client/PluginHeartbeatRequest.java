package com.autobanrobot.server.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PluginHeartbeatRequest(
    @NotBlank @Size(max = 64) String installationId,
    @NotBlank @Size(max = 32) String platform,
    @NotBlank @Size(max = 32) String version,
    @Size(max = 16) String clientType
) {
    public PluginHeartbeatRequest(String installationId, String platform, String version) {
        this(installationId, platform, version, "plugin");
    }
}
