package com.autobanrobot.server.client;

public record IpLocation(String label, Double latitude, Double longitude) {

    public static IpLocation unresolved() {
        return new IpLocation("", null, null);
    }

    public boolean isResolved() {
        return !label.isBlank() && latitude != null && longitude != null;
    }
}
