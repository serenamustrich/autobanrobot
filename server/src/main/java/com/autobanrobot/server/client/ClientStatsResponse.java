package com.autobanrobot.server.client;

public record ClientStatsResponse(
    ClientUserStatsResponse plugin,
    ClientUserStatsResponse app
) {
}
