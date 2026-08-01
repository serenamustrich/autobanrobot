package com.autobanrobot.server.rule;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record RuleConfigUpdateRequest(@NotNull JsonNode rules) {
}
