package com.autobanrobot.server.keyword;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/keywords")
public class KeywordAnalyticsController {

    private final KeywordAnalyticsService service;

    public KeywordAnalyticsController(KeywordAnalyticsService service) {
        this.service = service;
    }

    @GetMapping
    public List<KeywordRankingResponse> ranking(
        @RequestParam(defaultValue = "100") int limit
    ) {
        return service.ranking(limit);
    }
}
