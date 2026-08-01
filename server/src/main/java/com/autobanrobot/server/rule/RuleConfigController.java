package com.autobanrobot.server.rule;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@RestController
@RequestMapping("/api/rules")
public class RuleConfigController {

    private final RuleConfigService service;
    private final String adminToken;

    public RuleConfigController(
        RuleConfigService service,
        @Value("${autoban.rules.admin-token:}") String adminToken
    ) {
        this.service = service;
        this.adminToken = adminToken;
    }

    @GetMapping
    public RuleConfigResponse current() {
        return service.current();
    }

    @PutMapping
    public RuleConfigResponse update(
        @RequestHeader(value = "X-AutoBan-Admin-Token", required = false) String token,
        @Valid @RequestBody RuleConfigUpdateRequest request
    ) {
        authorize(token);
        return service.update(request.rules());
    }

    private void authorize(String token) {
        if (adminToken.isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "rule administration is disabled");
        }
        byte[] expected = adminToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = (token == null ? "" : token).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new ResponseStatusException(FORBIDDEN, "invalid admin token");
        }
    }
}
