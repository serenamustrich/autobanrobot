package com.autobanrobot.server.ban;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record BanEventRequest(
    @NotBlank @Size(max = 64) String clientEventId,
    @Size(max = 64) String installationId,
    @NotBlank @Size(max = 64) String username,
    @Size(max = 160) String displayName,
    @Size(max = 500) String reason,
    @Size(max = 30) List<@Size(max = 100) String> matchedKeywords,
    @Size(max = 1000) List<@Size(max = 100) String> configuredKeywords,
    @Size(max = 1000) String content,
    @Size(max = 1000) String pageUrl,
    Instant blockedAt,
    @Size(max = 16) String clientType
) {
}
