package com.autobanrobot.server.account;

import java.time.Instant;
import java.util.List;

public record AccountSettingsResponse(
    List<String> keywords,
    List<String> whitelist,
    Instant updatedAt,
    long revision
) {
}
