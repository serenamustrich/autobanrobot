package com.autobanrobot.server.release;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@RestController
@RequestMapping("/api/releases/latest")
public class ReleaseConfigController {

    private final ReleaseConfigService service;
    private final String adminToken;

    public ReleaseConfigController(
        ReleaseConfigService service,
        @Value("${autoban.release.admin-token:}") String adminToken
    ) {
        this.service = service;
        this.adminToken = adminToken;
    }

    @GetMapping
    public ResponseEntity<ReleaseConfigResponse> current() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().mustRevalidate().sMaxAge(0, TimeUnit.SECONDS))
            .body(service.current());
    }

    @PutMapping
    public ReleaseConfigResponse update(
        @RequestHeader(value = "X-AutoBan-Release-Token", required = false) String token,
        @Valid @RequestBody ReleaseConfigUpdateRequest request
    ) {
        authorize(token);
        return service.update(request);
    }

    private void authorize(String token) {
        if (adminToken.isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "release administration is disabled");
        }
        byte[] expected = adminToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = (token == null ? "" : token).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new ResponseStatusException(FORBIDDEN, "invalid release admin token");
        }
    }
}
