package com.autobanrobot.server.release;

import java.time.Instant;

public record ReleaseConfigResponse(String tag, String url, Instant updatedAt) {
}
