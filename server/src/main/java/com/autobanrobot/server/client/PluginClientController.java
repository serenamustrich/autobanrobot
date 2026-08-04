package com.autobanrobot.server.client;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.autobanrobot.server.account.AccountService;
import com.autobanrobot.server.account.DeviceBindRequest;

@RestController
@RequestMapping("/api/clients")
public class PluginClientController {

    private final PluginClientService service;
    private final ClientIpResolver clientIpResolver;
    private final AccountService accounts;

    public PluginClientController(PluginClientService service, ClientIpResolver clientIpResolver, AccountService accounts) {
        this.service = service;
        this.clientIpResolver = clientIpResolver;
        this.accounts = accounts;
    }

    @PostMapping("/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void heartbeat(
        @Valid @RequestBody PluginHeartbeatRequest request,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        HttpServletRequest servletRequest
    ) {
        if (authorization != null && !authorization.isBlank()) {
            accounts.bindDevice(authorization, new DeviceBindRequest(request.installationId()));
        }
        service.heartbeat(request, clientIpResolver.resolve(servletRequest));
    }

    @GetMapping("/stats")
    public ClientStatsResponse stats() {
        return service.stats();
    }
}
