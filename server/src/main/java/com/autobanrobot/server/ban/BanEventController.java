package com.autobanrobot.server.ban;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/bans")
public class BanEventController {

    private final BanEventService service;
    private final BanEventStream stream;

    public BanEventController(BanEventService service, BanEventStream stream) {
        this.service = service;
        this.stream = stream;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BanEventResponse receive(@Valid @RequestBody BanEventRequest request, @RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.receive(request, authorization);
    }

    @GetMapping
    public BanPageResponse list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "30") int size,
        @RequestParam(defaultValue = "") String query
    ) {
        return service.list(page, size, query);
    }

    @GetMapping("/stats")
    public BanStatsResponse stats() {
        return service.stats();
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return stream.subscribe();
    }
}
