package com.autobanrobot.server.account;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/account/settings")
public class AccountSettingsController {

    private final AccountService service;
    private final AccountSettingsStream stream;

    public AccountSettingsController(AccountService service, AccountSettingsStream stream) {
        this.service = service;
        this.stream = stream;
    }

    @GetMapping
    public AccountSettingsResponse get(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.settings(authorization);
    }

    @PutMapping
    public AccountSettingsResponse replace(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody AccountSettingsRequest request
    ) {
        return service.replaceSettings(authorization, request, false);
    }

    @PostMapping("/merge")
    public AccountSettingsResponse merge(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody AccountSettingsRequest request
    ) {
        return service.replaceSettings(authorization, request, true);
    }

    @GetMapping("/stream")
    public SseEmitter stream(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return stream.subscribe(service.requireAccount(authorization).getId());
    }
}
