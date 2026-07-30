package com.autobanrobot.server.client;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients")
public class PluginClientController {

    private final PluginClientService service;

    public PluginClientController(PluginClientService service) {
        this.service = service;
    }

    @PostMapping("/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void heartbeat(@Valid @RequestBody PluginHeartbeatRequest request) {
        service.heartbeat(request);
    }

    @GetMapping("/stats")
    public PluginUserStatsResponse stats() {
        return service.stats();
    }
}
