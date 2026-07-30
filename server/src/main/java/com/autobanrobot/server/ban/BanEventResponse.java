package com.autobanrobot.server.ban;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public record BanEventResponse(
    Long id,
    String clientEventId,
    String username,
    String displayName,
    String reason,
    List<String> matchedKeywords,
    List<String> configuredKeywords,
    String content,
    String pageUrl,
    Instant blockedAt,
    Instant receivedAt
) {
    public static BanEventResponse from(BanEvent event) {
        return new BanEventResponse(
            event.getId(),
            event.getClientEventId(),
            event.getUsername(),
            event.getDisplayName(),
            event.getReason(),
            splitKeywords(event.getMatchedKeywords()),
            splitKeywords(event.getConfiguredKeywords()),
            event.getContent(),
            event.getPageUrl(),
            event.getBlockedAt(),
            event.getReceivedAt()
        );
    }

    private static List<String> splitKeywords(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\n"))
            .filter(keyword -> !keyword.isBlank())
            .toList();
    }
}
