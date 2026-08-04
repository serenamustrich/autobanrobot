package com.autobanrobot.server.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

@Service
public class AdminPortalAccessService {

    private final String firstTokenHash;
    private final String secondTokenHash;

    public AdminPortalAccessService(
        @Value("${autoban.admin-portal.first-token-sha256:}") String firstTokenHash,
        @Value("${autoban.admin-portal.second-token-sha256:}") String secondTokenHash
    ) {
        this.firstTokenHash = firstTokenHash;
        this.secondTokenHash = secondTokenHash;
    }

    public void requireAccess(String firstToken, String secondToken) {
        if (!matches(firstTokenHash, firstToken) || !matches(secondTokenHash, secondToken)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private boolean matches(String configuredHash, String token) {
        if (!configuredHash.matches("(?i)^[0-9a-f]{64}$") || token == null || token.length() != 64) return false;
        byte[] expected = configuredHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = sha256(token).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
