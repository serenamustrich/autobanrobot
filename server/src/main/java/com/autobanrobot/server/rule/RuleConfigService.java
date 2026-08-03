package com.autobanrobot.server.rule;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import jakarta.transaction.Transactional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class RuleConfigService {

    private static final long SINGLETON_ID = 1L;
    private static final int MAX_RULES = 100;
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
    public synchronized RuleConfigResponse update(JsonNode rules) {
        validate(rules);
        RuleConfig config = repository.findById(SINGLETON_ID).orElseGet(this::createDefault);
        config.update(config.getVersion() + 1, rules.toString(), Instant.now());
        return response(repository.save(config));
    }

    private RuleConfig createDefault() {
        try {
            JsonNode root = readBundledRules();
            JsonNode rules = root.path("rules");
            validate(rules);
            long version = Math.max(1, root.path("version").asLong(1));
            return repository.save(new RuleConfig(
                SINGLETON_ID, version, rules.toString(), Instant.now()
            ));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load default rules", exception);
        }
    }

    private RuleConfig upgradeBundledRules(RuleConfig config) {
        try {
            JsonNode root = readBundledRules();
            long bundledVersion = Math.max(1, root.path("version").asLong(1));
            if (config.getVersion() >= bundledVersion) return config;

            ArrayNode currentRules = (ArrayNode) objectMapper.readTree(config.getRulesJson());
            Set<String> currentIds = new HashSet<>();
            currentRules.forEach(rule -> currentIds.add(rule.path("id").asText()));
            root.path("rules").forEach(rule -> {
                if (currentIds.add(rule.path("id").asText())) currentRules.add(rule.deepCopy());
            });
            validate(currentRules);
            config.update(bundledVersion, currentRules.toString(), Instant.now());
            return repository.save(config);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to upgrade bundled rules", exception);
        }
    }

    private JsonNode readBundledRules() throws IOException {
        return objectMapper.readTree(
            new ClassPathResource("default-rules.json").getInputStream()
        );
    }

    private RuleConfigResponse response(RuleConfig config) {
        return new RuleConfigResponse(
            config.getVersion(),
            config.getUpdatedAt(),
            objectMapper.readTree(config.getRulesJson())
        );
    }

    private void validate(JsonNode rules) {
        if (!rules.isArray() || rules.size() > MAX_RULES) {
            throw new ResponseStatusException(BAD_REQUEST, "rules must be an array with at most 100 items");
        }
        for (JsonNode rule : rules) {
            String id = rule.path("id").asText();
            String name = rule.path("name").asText();
            String pattern = rule.path("pattern").asText();
            String flags = rule.path("flags").asText("");
            String matcher = rule.path("matcher").asText("");
            String scope = rule.path("scope").asText("content");
            String normalization = rule.path("normalization").asText("raw");
            boolean validPattern = matcher.isBlank() &&
                !pattern.isBlank() && pattern.length() <= MAX_PATTERN_LENGTH &&
                flags.matches("[gimsuy]*");
            boolean validMatcher = pattern.isBlank() &&
                (matcher.equals("singleEmoji") ||
                 matcher.equals("structuredEmojiTime") ||
                 matcher.equals("structuredThreeSegment") ||
                 matcher.equals("structuredFourSegmentCodeEmoji"));
            if (id.isBlank() || id.length() > 64 || name.isBlank() || name.length() > 120 ||
                !(validPattern || validMatcher) ||
                !(scope.equals("content") || scope.equals("username") || scope.equals("displayName")) ||
                (rule.has("requiresDefaultAvatar") && !rule.path("requiresDefaultAvatar").isBoolean()) ||
                !(normalization.equals("raw") || normalization.equals("compact") || normalization.equals("noSymbols"))) {
                throw new ResponseStatusException(BAD_REQUEST, "invalid rule: " + id);
            }
        }
    }
}
