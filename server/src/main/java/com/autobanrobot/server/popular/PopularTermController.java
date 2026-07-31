package com.autobanrobot.server.popular;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/popular-terms")
public class PopularTermController {

    private final PopularTermService service;

    public PopularTermController(PopularTermService service) {
        this.service = service;
    }

    @GetMapping
    public List<PopularTermResponse> ranking(
        @RequestParam(defaultValue = "100") int limit
    ) {
        return service.ranking(limit);
    }
}
