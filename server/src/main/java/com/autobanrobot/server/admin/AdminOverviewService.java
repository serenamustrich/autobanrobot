package com.autobanrobot.server.admin;

import com.autobanrobot.server.ban.BanEventRepository;
import com.autobanrobot.server.account.AccountContributionRepository;
import com.autobanrobot.server.client.PluginClient;
import com.autobanrobot.server.client.PluginClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminOverviewService {

    private static final Duration ONLINE_WINDOW = Duration.ofMinutes(2);
    private final PluginClientRepository clients;
    private final BanEventRepository banEvents;
    private final AccountContributionRepository contributions;

    public AdminOverviewService(PluginClientRepository clients, BanEventRepository banEvents, AccountContributionRepository contributions) {
        this.clients = clients;
        this.banEvents = banEvents;
        this.contributions = contributions;
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse overview() {
        Instant now = Instant.now();
        Instant onlineThreshold = now.minus(ONLINE_WINDOW);
        Map<String, Long> contributionByInstallation = banEvents.countContributionsByInstallationId().stream()
            .collect(Collectors.toMap(
                BanEventRepository.InstallationContribution::getInstallationId,
                BanEventRepository.InstallationContribution::getBanCount,
                Long::sum
            ));
        List<PluginClient> clientRows = clients.findTop500ByOrderByLastSeenAtDesc();
        List<AdminOverviewResponse.AdminDeviceRow> devices = clientRows.stream()
            .map(client -> deviceRow(client, contributionByInstallation, onlineThreshold))
            .toList();
        List<AdminOverviewResponse.AdminMapMarker> markers = clientRows.stream()
            .filter(client -> !client.getLastSeenAt().isBefore(onlineThreshold))
            .filter(client -> client.getLocationLatitude() != null && client.getLocationLongitude() != null)
            .map(client -> marker(client, contributionByInstallation.getOrDefault(client.getInstallationId(), 0L)))
            .toList();
        long onlineDevices = clients.countByLastSeenAtGreaterThanEqualAndClientType(onlineThreshold, "plugin")
            + clients.countByLastSeenAtGreaterThanEqualAndClientType(onlineThreshold, "app");
        // Logged-in accounts are counted once per target across every bound device.
        // Pre-account historical events remain visible in device rows until claimed.
        long totalContribution = contributions.count();
        Long onlineSeconds = clients.sumOnlineSeconds();
        long totalOnlineSeconds = onlineSeconds == null ? 0 : onlineSeconds;
        return new AdminOverviewResponse(
            now,
            new AdminOverviewResponse.AdminSummary(clients.count(), onlineDevices, totalContribution, totalOnlineSeconds),
            devices,
            markers
        );
    }

    private AdminOverviewResponse.AdminDeviceRow deviceRow(
        PluginClient client,
        Map<String, Long> contributionByInstallation,
        Instant onlineThreshold
    ) {
        String deviceName = client.getDeviceName().isBlank() ? "未命名设备" : client.getDeviceName();
        String deviceType = ("app".equals(client.getClientType()) ? "App" : "插件") + " · " + client.getPlatform();
        return new AdminOverviewResponse.AdminDeviceRow(
            deviceName,
            deviceType,
            client.getPluginVersion(),
            !client.getLastSeenAt().isBefore(onlineThreshold),
            client.getLastSeenAt(),
            client.getOnlineSeconds(),
            contributionByInstallation.getOrDefault(client.getInstallationId(), 0L)
        );
    }

    private AdminOverviewResponse.AdminMapMarker marker(PluginClient client, long contribution) {
        String label = client.getDeviceName().isBlank() ? client.getPlatform() : client.getDeviceName();
        String deviceType = "app".equals(client.getClientType()) ? "App" : "插件";
        return new AdminOverviewResponse.AdminMapMarker(
            label,
            deviceType,
            contribution,
            client.getLocationLabel(),
            client.getLocationLatitude(),
            client.getLocationLongitude()
        );
    }
}
