package com.autobanrobot.server.account;

import jakarta.validation.constraints.Size;

import java.util.List;

public record AccountSettingsRequest(
    @Size(max = 1000) List<@Size(max = 100) String> keywords,
    @Size(max = 1000) List<@Size(max = 15) String> whitelist
) {
}
