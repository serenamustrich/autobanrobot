package com.autobanrobot.server.rule;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record RuleConfigResponse(long version, Instant updatedAt, JsonNode rules) {
}
