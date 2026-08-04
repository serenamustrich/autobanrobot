package com.autobanrobot.server.admin;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminPortalAccessServiceTest {

    private static final String FIRST_TOKEN = "e4d57a04d428e9a7b20d6b1bc5df34afc4743463196334cfed6746e90d803d4a";
    private static final String SECOND_TOKEN = "5d9b6480fb34a1df83290092d334da0d4d3d4e49b611d6f5e7302e0ad7a0e679";

    @Test
    void acceptsOnlyBothMatchingRouteTokens() {
        AdminPortalAccessService service = new AdminPortalAccessService(sha256(FIRST_TOKEN), sha256(SECOND_TOKEN));

        assertDoesNotThrow(() -> service.requireAccess(FIRST_TOKEN, SECOND_TOKEN));
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.requireAccess(FIRST_TOKEN, FIRST_TOKEN)
        );
        assertEquals(404, exception.getStatusCode().value());
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
