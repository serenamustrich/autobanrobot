package com.autobanrobot.server.client;

public record PluginUserStatsResponse(
    long onlineUsers,
    long cumulativeUsers,
    long onlineWindowSeconds
) {
}
