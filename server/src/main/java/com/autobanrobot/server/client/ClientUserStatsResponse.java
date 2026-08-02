package com.autobanrobot.server.client;

public record ClientUserStatsResponse(
    long onlineUsers,
    long cumulativeUsers,
    long onlineWindowSeconds
) {
}
