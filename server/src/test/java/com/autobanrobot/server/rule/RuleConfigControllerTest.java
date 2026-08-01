package com.autobanrobot.server.rule;

import com.autobanrobot.server.config.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RuleConfigControllerTest {

    private MockMvc mockMvc;
    private RuleConfigService service;

    @BeforeEach
    void setUp() {
        service = mock(RuleConfigService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new RuleConfigController(service, "test-secret"))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @Test
    void returnsPublicRuleConfiguration() throws Exception {
        var rules = JsonMapper.builder().build().readTree("""
            [{"id":"sample","name":"Sample","pattern":"spam","flags":"iu"}]
            """);
        when(service.current()).thenReturn(new RuleConfigResponse(
            3, Instant.parse("2026-08-01T01:02:03Z"), rules
        ));

        mockMvc.perform(get("/api/rules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(3))
            .andExpect(jsonPath("$.rules[0].id").value("sample"));
    }

    @Test
    void protectsRuleUpdatesWithAdminToken() throws Exception {
        mockMvc.perform(put("/api/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rules\":[]}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void acceptsAuthorizedRuleUpdates() throws Exception {
        var rules = JsonMapper.builder().build().readTree("[]");
        when(service.update(any())).thenReturn(new RuleConfigResponse(
            4, Instant.parse("2026-08-01T01:02:03Z"), rules
        ));

        mockMvc.perform(put("/api/rules")
                .header("X-AutoBan-Admin-Token", "test-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rules\":[]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(4));
    }
}
