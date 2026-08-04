package com.autobanrobot.server.rule;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuleConfigServiceTest {

    @Test
    void bundledRulePackageContainsDeclarativeKeywordAndAccountRules() {
        RuleConfigRepository repository = mock(RuleConfigRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.empty());
        when(repository.save(any(RuleConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RuleConfigService service = new RuleConfigService(repository, JsonMapper.builder().build());

        RuleConfigResponse response = service.current();

        assertEquals(1, response.engine().path("schemaVersion").asInt());
        assertTrue(response.keywordPolicies().size() >= 3);
        assertEquals("complete-account-target", response.accountPolicies().get(0).path("id").asText());
        assertTrue(response.accountPolicies().get(0).path("targets").toString().contains("username"));
        assertTrue(response.accountPolicies().get(0).path("targets").toString().contains("content"));
        assertTrue(response.rules().get(0).has("condition"));
    }

    @Test
    void updatesKeywordSetsWithoutReplacingRules() throws Exception {
        var mapper = JsonMapper.builder().build();
        RuleConfigRepository repository = mock(RuleConfigRepository.class);
        RuleConfig stored = new RuleConfig(
            1L,
            12L,
            """
            {"engine":{"schemaVersion":1},"keywordSets":[],"keywordPolicies":[],
             "accountPolicies":[],"rules":[]}
            """,
            java.time.Instant.now()
        );
        when(repository.findById(1L)).thenReturn(Optional.of(stored));
        when(repository.save(any(RuleConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RuleConfigService service = new RuleConfigService(repository, mapper);
        var keywordSets = mapper.readTree("""
            [{"id":"online","keywords":["测试词"]}]
            """);

        RuleConfigResponse response = service.update(new RuleConfigUpdateRequest(
            null, null, keywordSets, null, null
        ));

        assertEquals("测试词", response.keywordSets().get(0).path("keywords").get(0).asText());
        assertEquals(0, response.rules().size());
    }

}
