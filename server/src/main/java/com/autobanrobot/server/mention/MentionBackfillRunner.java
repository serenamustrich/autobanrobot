package com.autobanrobot.server.mention;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MentionBackfillRunner implements ApplicationRunner {

    private final MentionAnalyticsService service;

    public MentionBackfillRunner(MentionAnalyticsService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        service.backfill();
    }
}
