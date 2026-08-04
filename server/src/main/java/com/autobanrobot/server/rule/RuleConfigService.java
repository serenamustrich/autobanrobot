package com.autobanrobot.server.rule;

import jakarta.transaction.Transactional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Persists a declarative rule package rather than executable code. The client
 * has one vetted interpreter; this service only publishes JSON data for it.
 */
@Service
public class RuleConfigService {

    private static final long SINGLETON_ID = 1L;
    private static final int MAX_RULES = 100;
    private static final int MAX_KEYWORD_SETS = 50;
    private static final int MAX_KEYWORD_POLICIES = 50;
    private static final int MAX_ACCOUNT_POLICIES = 50;
    private static final int MAX_PATTERN_LENGTH = 2_000;

    private final RuleConfigRepository repository;
    private final ObjectMapper objectMapper;

    public RuleConfigService(RuleConfigRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RuleConfigResponse current() {
        RuleConfig config = repository.findById(SINGLETON_ID).orElseGet(this::createDefault);
        return response(upgradeBundledRules(config));
    }

    @Transactional
    public synchronized RuleConfigResponse update(RuleConfigUpdateRequest request) {
        RuleConfig config = repository.findById(SINGLETON_ID).orElseGet(this::createDefault);
        ObjectNode next = storedConfig(config).deepCopy();
        if (request.rules() != null && !request.rules().isNull()) {
            next.set("rules", request.rules());
        }
        if (request.engine() != null && !request.engine().isNull()) {
            next.set("engine", request.engine());
        }
        if (request.keywordSets() != null && !request.keywordSets().isNull()) {
            next.set("keywordSets", request.keywordSets());
        }
        if (request.keywordPolicies() != null && !request.keywordPolicies().isNull()) {
            next.set("keywordPolicies", request.keywordPolicies());
        }
        if (request.accountPolicies() != null && !request.accountPolicies().isNull()) {
            next.set("accountPolicies", request.accountPolicies());
        }
        validateConfig(next);
        config.update(config.getVersion() + 1, next.toString(), Instant.now());
        return response(repository.save(config));
    }

    private RuleConfig createDefault() {
        try {
            ObjectNode root = requireObject(readBundledRules(), "bundled rule configuration");
            validateConfig(root);
            long version = Math.max(1, root.path("version").asLong(1));
            return repository.save(new RuleConfig(SINGLETON_ID, version, root.toString(), Instant.now()));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load default rules", exception);
        }
    }

    /** Migrates legacy array storage and backfills bundled interpreter metadata. */
    private RuleConfig upgradeBundledRules(RuleConfig config) {
        try {
            ObjectNode bundled = requireObject(readBundledRules(), "bundled rule configuration");
            ObjectNode current = storedConfig(config);
            ArrayNode currentRules = requireArray(current.path("rules"), "stored rules");
            ArrayNode bundledRules = requireArray(bundled.path("rules"), "bundled rules");
            Map<String, JsonNode> bundledById = new HashMap<>();
            bundledRules.forEach(rule -> bundledById.put(rule.path("id").asText(), rule));
            Set<String> currentIds = new HashSet<>();
            boolean changed = false;

            for (int index = 0; index < currentRules.size(); index++) {
                JsonNode existing = currentRules.get(index);
                String id = existing.path("id").asText();
                currentIds.add(id);
                JsonNode replacement = bundledById.get(id);
                // These IDs were previously hard-coded matcher names. Replace
                // only them with their equivalent declarative conditions.
                if (existing.has("matcher") && replacement != null && replacement.has("condition")) {
                    currentRules.set(index, replacement.deepCopy());
                    changed = true;
                }
            }
            for (JsonNode rule : bundledRules) {
                if (currentIds.add(rule.path("id").asText())) {
                    currentRules.add(rule.deepCopy());
                    changed = true;
                }
            }
            if (!current.has("engine") && bundled.has("engine")) {
                current.set("engine", bundled.get("engine").deepCopy());
                changed = true;
            }
            if (!current.has("keywordSets") && bundled.has("keywordSets")) {
                current.set("keywordSets", bundled.get("keywordSets").deepCopy());
                changed = true;
            }
            if (!current.has("keywordPolicies") && bundled.has("keywordPolicies")) {
                current.set("keywordPolicies", bundled.get("keywordPolicies").deepCopy());
                changed = true;
            }
            if ((!current.has("accountPolicies") || current.path("accountPolicies").isEmpty()) &&
                bundled.has("accountPolicies")) {
                current.set("accountPolicies", bundled.get("accountPolicies").deepCopy());
                changed = true;
            }

            long bundledVersion = Math.max(1, bundled.path("version").asLong(1));
            long nextVersion = Math.max(config.getVersion(), bundledVersion);
            if (nextVersion != config.getVersion()) changed = true;
            if (!changed) return config;

            validateConfig(current);
            config.update(nextVersion, current.toString(), Instant.now());
            return repository.save(config);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to upgrade bundled rules", exception);
        }
    }

    private ObjectNode storedConfig(RuleConfig config) {
        JsonNode stored = objectMapper.readTree(config.getRulesJson());
        if (stored.isArray()) {
            ObjectNode legacy = objectMapper.createObjectNode();
            legacy.set("rules", stored);
            return legacy;
        }
        return requireObject(stored, "stored rule configuration");
    }

    private JsonNode readBundledRules() throws IOException {
        return objectMapper.readTree(new ClassPathResource("default-rules.json").getInputStream());
    }

    private RuleConfigResponse response(RuleConfig config) {
        ObjectNode root = storedConfig(config);
        return new RuleConfigResponse(
            config.getVersion(),
            config.getUpdatedAt(),
            root.get("engine"),
            root.get("keywordSets"),
            root.get("keywordPolicies"),
            root.get("accountPolicies"),
            root.path("rules")
        );
    }

    private void validateConfig(ObjectNode config) {
        JsonNode engine = config.path("engine");
        if (!engine.isObject() || engine.path("schemaVersion").asInt(-1) != 1) {
            invalid("unsupported rule engine");
        }
        validateKeywordSets(config.path("keywordSets"));
        validateKeywordPolicies(config.path("keywordPolicies"));
        validateAccountPolicies(config.path("accountPolicies"));
        validateRules(config.path("rules"));
    }

    private void validateKeywordSets(JsonNode sets) {
        if (!sets.isArray() || sets.size() > MAX_KEYWORD_SETS) {
            invalid("keywordSets must be an array with at most 50 items");
        }
        for (JsonNode set : sets) {
            JsonNode keywords = set.path("keywords");
            if (!validText(set.path("id").asText(), 64) ||
                (set.has("enabled") && !set.path("enabled").isBoolean()) ||
                !keywords.isArray() || keywords.size() > 1_000) {
                invalid("invalid keyword set");
            }
            for (JsonNode keyword : keywords) {
                if (!keyword.isTextual() || !validText(keyword.asText().trim(), 100)) {
                    invalid("invalid keyword");
                }
            }
        }
    }

    private void validateKeywordPolicies(JsonNode policies) {
        if (!policies.isArray() || policies.size() > MAX_KEYWORD_POLICIES) {
            invalid("keywordPolicies must be an array with at most 50 items");
        }
        for (JsonNode policy : policies) {
            JsonNode scopes = policy.path("scopes");
            String operator = policy.path("operator").asText();
            String normalization = policy.path("normalization").asText();
            if (!validText(policy.path("id").asText(), 64) || !scopes.isArray() ||
                scopes.isEmpty() || scopes.size() > 3 ||
                !(operator.equals("includes") || operator.equals("token")) ||
                !validKeywordNormalization(normalization) ||
                !policy.path("minLength").canConvertToInt() ||
                policy.path("minLength").asInt() < 1 || policy.path("minLength").asInt() > 100 ||
                (policy.has("keywordPattern") && !validText(policy.path("keywordPattern").asText(), 500)) ||
                !policy.path("keywordFlags").asText("").matches("[imsuy]*") ||
                !policy.path("flags").asText("").matches("[imsuy]*")) {
                invalid("invalid keyword policy");
            }
            for (JsonNode scope : scopes) {
                String value = scope.asText();
                if (!(value.equals("content") || value.equals("username") || value.equals("displayName"))) {
                    invalid("invalid keyword scope");
                }
            }
        }
    }

    private void validateAccountPolicies(JsonNode policies) {
        if (!policies.isArray() || policies.size() > MAX_ACCOUNT_POLICIES) {
            invalid("accountPolicies must be an array with at most 50 items");
        }
        for (JsonNode policy : policies) {
            if (!validText(policy.path("id").asText(), 64) ||
                !validText(policy.path("keywordPattern").asText(), 500) ||
                !policy.path("keywordFlags").asText("").matches("[imsuy]*")) {
                invalid("invalid account policy");
            }
            JsonNode targets = policy.path("targets");
            if (!targets.isArray() || targets.isEmpty() || targets.size() > 3) invalid("invalid account targets");
            for (JsonNode target : targets) {
                String scope = target.path("scope").asText();
                String pattern = target.path("pattern").asText();
                String flags = target.path("flags").asText("");
                String normalization = target.path("normalization").asText("raw");
                if (!(scope.equals("content") || scope.equals("username")) ||
                    !validText(pattern, MAX_PATTERN_LENGTH) || !pattern.matches(".*\\{\\{[1-9]}}.*") ||
                    !flags.matches("[imsuy]*") || !validNormalization(normalization)) {
                    invalid("invalid account target");
                }
            }
        }
    }

    private void validateRules(JsonNode rules) {
        if (!rules.isArray() || rules.size() > MAX_RULES) {
            invalid("rules must be an array with at most 100 items");
        }
        for (JsonNode rule : rules) {
            String id = rule.path("id").asText();
            String name = rule.path("name").asText();
            String pattern = rule.path("pattern").asText();
            String flags = rule.path("flags").asText("");
            String matcher = rule.path("matcher").asText("");
            String scope = rule.path("scope").asText("content");
            String normalization = rule.path("normalization").asText("raw");
            boolean validCondition = rule.has("condition") && validateCondition(rule.get("condition"), 0);
            boolean validPattern = !rule.has("condition") && matcher.isBlank() &&
                validText(pattern, MAX_PATTERN_LENGTH) && flags.matches("[gimsuy]*");
            boolean validMatcher = !rule.has("condition") && pattern.isBlank() &&
                (matcher.equals("singleEmoji") || matcher.equals("structuredEmojiTime") ||
                 matcher.equals("structuredThreeSegment") || matcher.equals("structuredFourSegmentCodeEmoji"));
            if (!validText(id, 64) || !validText(name, 120) || !(validCondition || validPattern || validMatcher) ||
                !(scope.equals("content") || scope.equals("username") || scope.equals("displayName")) ||
                (rule.has("requiresDefaultAvatar") && !rule.path("requiresDefaultAvatar").isBoolean()) ||
                !validNormalization(normalization)) {
                invalid("invalid rule: " + id);
            }
        }
    }

    private boolean validateCondition(JsonNode condition, int depth) {
        if (!condition.isObject() || depth > 12) return false;
        if (condition.has("all") || condition.has("any")) {
            JsonNode group = condition.has("all") ? condition.path("all") : condition.path("any");
            if (!group.isArray() || group.isEmpty() || group.size() > 32) return false;
            for (JsonNode item : group) if (!validateCondition(item, depth + 1)) return false;
            return true;
        }
        if (condition.has("not")) return validateCondition(condition.get("not"), depth + 1);
        String type = condition.path("type").asText();
        if (type.equals("singleEmoji")) return true;
        if (type.equals("graphemeCount") || type.equals("lineCount")) return validCount(condition);
        if (type.equals("lineAt")) {
            return condition.path("index").canConvertToInt() && condition.path("index").asInt() >= 0 &&
                validateCondition(condition.path("condition"), depth + 1);
        }
        if (type.equals("anyLine") || type.equals("allLines") || type.equals("countLines")) {
            if (!validateCondition(condition.path("condition"), depth + 1)) return false;
            if (condition.has("where") && !validateCondition(condition.get("where"), depth + 1)) return false;
            return !type.equals("countLines") || validCount(condition);
        }
        return type.equals("regex") && validText(condition.path("pattern").asText(), MAX_PATTERN_LENGTH) &&
            condition.path("flags").asText("").matches("[imsuy]*") &&
            validNormalization(condition.path("normalization").asText("raw"));
    }

    private boolean validCount(JsonNode condition) {
        boolean specified = false;
        for (String key : new String[]{"equals", "min", "max"}) {
            if (!condition.has(key)) continue;
            specified = true;
            if (!condition.path(key).canConvertToInt() || condition.path(key).asInt() < 0) return false;
        }
        return specified && !(condition.has("min") && condition.has("max") &&
            condition.path("min").asInt() > condition.path("max").asInt());
    }

    private boolean validText(String value, int maxLength) {
        return !value.isBlank() && value.length() <= maxLength;
    }

    private boolean validNormalization(String value) {
        return value.equals("raw") || value.equals("compact") || value.equals("noSymbols");
    }

    private boolean validKeywordNormalization(String value) {
        return validNormalization(value) || value.equals("hanNoise");
    }

    private ObjectNode requireObject(JsonNode node, String label) {
        if (node instanceof ObjectNode object) return object;
        invalid(label + " must be an object");
        throw new IllegalStateException("unreachable");
    }

    private ArrayNode requireArray(JsonNode node, String label) {
        if (node instanceof ArrayNode array) return array;
        invalid(label + " must be an array");
        throw new IllegalStateException("unreachable");
    }

    private void invalid(String message) {
        throw new ResponseStatusException(BAD_REQUEST, message);
    }
}
