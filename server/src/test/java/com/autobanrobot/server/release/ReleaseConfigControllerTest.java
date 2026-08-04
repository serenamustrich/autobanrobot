package com.autobanrobot.server.release;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;

import com.autobanrobot.server.config.ApiExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReleaseConfigControllerTest {

    private MockMvc mockMvc;
    private ReleaseConfigService service;

    @BeforeEach
    void setUp() {
        service = mock(ReleaseConfigService.class);
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ReleaseConfigController(service, "release-token"))
            .setControllerAdvice(new ApiExceptionHandler())
            .setValidator(validator)
            .build();
    }

    @Test
    void servesLatestReleaseWithoutCaching() throws Exception {
        when(service.current()).thenReturn(new ReleaseConfigResponse(
            "v1.6.29",
            "https://github.com/serenamustrich/autobanrobot/releases/tag/v1.6.29",
            Instant.parse("2026-08-03T05:00:00Z")
        ));

        mockMvc.perform(get("/api/releases/latest"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(jsonPath("$.tag").value("v1.6.29"))
            .andExpect(jsonPath("$.url").value(
                "https://github.com/serenamustrich/autobanrobot/releases/tag/v1.6.29"
            ));
    }

    @Test
    void updatesLatestReleaseOnlyWithAdminToken() throws Exception {
        when(service.update(any())).thenReturn(new ReleaseConfigResponse(
            "v1.6.29",
            "https://github.com/serenamustrich/autobanrobot/releases/tag/v1.6.29",
            Instant.now()
        ));

        mockMvc.perform(put("/api/releases/latest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tag":"v1.6.29","url":"https://github.com/serenamustrich/autobanrobot/releases/tag/v1.6.29"}
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/releases/latest")
                .header("X-AutoBan-Release-Token", "release-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tag":"v1.6.29","url":"https://github.com/serenamustrich/autobanrobot/releases/tag/v1.6.29"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tag").value("v1.6.29"));
        verify(service).update(any());
    }
}
