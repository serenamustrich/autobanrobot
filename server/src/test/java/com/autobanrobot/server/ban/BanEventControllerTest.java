package com.autobanrobot.server.ban;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.autobanrobot.server.config.ApiExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BanEventControllerTest {

    private MockMvc mockMvc;
    private BanEventService service;

    @BeforeEach
    void setUp() {
        service = mock(BanEventService.class);
        BanEventStream stream = mock(BanEventStream.class);
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
            .standaloneSetup(new BanEventController(service, stream))
            .setControllerAdvice(new ApiExceptionHandler())
            .setValidator(validator)
            .build();
    }

    @Test
    void acceptsAConfirmedBanEvent() throws Exception {
        Instant blockedAt = Instant.parse("2026-07-30T01:02:03Z");
        when(service.receive(any())).thenReturn(new BanEventResponse(
            1L,
            "evt-test-001",
            "spam_account",
            "Spam Account",
            "内容命中关键词",
            List.of("同城"),
            List.of("同城", "上门", "主页联系"),
            "同城",
            "https://x.com/home",
            blockedAt,
            blockedAt
        ));

        mockMvc.perform(post("/api/bans")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "clientEventId": "evt-test-001",
                      "username": "@spam_account",
                      "matchedKeywords": ["同城"],
                      "configuredKeywords": ["同城", "上门", "主页联系"],
                      "blockedAt": "2026-07-30T01:02:03Z"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("spam_account"))
            .andExpect(jsonPath("$.matchedKeywords[0]").value("同城"))
            .andExpect(jsonPath("$.configuredKeywords[2]").value("主页联系"));
    }

    @Test
    void rejectsInvalidPayloads() throws Exception {
        mockMvc.perform(post("/api/bans")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"clientEventId":"","username":""}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }
}
