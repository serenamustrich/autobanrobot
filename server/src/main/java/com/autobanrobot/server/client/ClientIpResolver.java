package com.autobanrobot.server.client;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ClientIpResolver {

    private final Set<String> trustedProxies;

    public ClientIpResolver(@Value("${autoban.client-ip.trusted-proxies:127.0.0.1,::1}") String trustedProxies) {
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!trustedProxies.contains(remoteAddress)) return validAddress(remoteAddress) ? remoteAddress : "";
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null) {
            String candidate = forwarded.split(",", 2)[0].trim();
            if (validAddress(candidate)) return candidate;
        }
        String realIp = request.getHeader("X-Real-IP");
        return validAddress(realIp) ? realIp.trim() : (validAddress(remoteAddress) ? remoteAddress : "");
    }

    private boolean validAddress(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            InetAddress.getByName(value.trim());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
