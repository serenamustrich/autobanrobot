package com.autobanrobot.server.mention;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mentions")
public class MentionAnalyticsController {

    private final MentionAnalyticsService service;

    public MentionAnalyticsController(MentionAnalyticsService service) {
        this.service = service;
    }

    @GetMapping
    public List<MentionRankingResponse> ranking(
        @RequestParam(defaultValue = "100") int limit
    ) {
        return service.ranking(limit);
    }
}
