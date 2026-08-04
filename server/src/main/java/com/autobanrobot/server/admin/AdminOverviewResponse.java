package com.autobanrobot.server.admin;

import java.time.Instant;
import java.util.List;

public record AdminOverviewResponse(
    Instant generatedAt,
    AdminSummary summary,
    List<AdminDeviceRow> devices,
    List<AdminMapMarker> onlineMarkers
) {
    public record AdminSummary(long totalDevices, long onlineDevices, long totalContribution, long totalOnlineSeconds) { }

    public record AdminDeviceRow(
        String deviceName,
        String deviceType,
        String version,
        boolean online,
        Instant lastSeenAt,
        long onlineSeconds,
        long contribution
    ) { }

    public record AdminMapMarker(
        String label,
        String deviceType,
        long contribution,
        String location,
        double latitude,
        double longitude
    ) { }
}
