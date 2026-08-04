package com.autobanrobot.server.rule;

import tools.jackson.databind.JsonNode;

public record RuleConfigUpdateRequest(
    JsonNode rules,
    JsonNode engine,
    JsonNode keywordSets,
    JsonNode keywordPolicies,
    JsonNode accountPolicies
) {
}
