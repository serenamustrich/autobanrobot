package com.autobanrobot.server.account;

import java.time.Instant;

public record AccountSessionResponse(String accessToken, String username, Instant expiresAt) {
}
