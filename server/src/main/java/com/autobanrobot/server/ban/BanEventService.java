package com.autobanrobot.server.ban;

import com.autobanrobot.server.keyword.KeywordAnalyticsService;
import com.autobanrobot.server.mention.MentionAnalyticsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Duration;

@Service
public class BanEventService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String APP = "app";
    private static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(10);

    private final BanEventRepository repository;
    private final BanEventStream stream;
    private final KeywordAnalyticsService keywordAnalytics;
    private final MentionAnalyticsService mentionAnalytics;

    public BanEventService(
        BanEventRepository repository,
        BanEventStream stream,
        KeywordAnalyticsService keywordAnalytics,
        MentionAnalyticsService mentionAnalytics
    ) {
        this.repository = repository;
        this.stream = stream;
        this.keywordAnalytics = keywordAnalytics;
        this.mentionAnalytics = mentionAnalytics;
    }

    @Transactional
    public BanEventResponse receive(BanEventRequest request) {
        var existing = repository.findByClientEventId(request.clientEventId());
        if (existing.isPresent()) {
            return BanEventResponse.from(existing.get());
        }

        Instant now = Instant.now();
        String username = cleanUsername(request.username());
        String pageUrl = safe(request.pageUrl());
        String content = safe(request.content());
        var duplicate = repository
            .findTopByUsernameIgnoreCaseAndPageUrlAndContentAndBlockedAtGreaterThanEqualOrderByBlockedAtDesc(
                username,
                pageUrl,
                content,
                now.minus(DUPLICATE_WINDOW)
            );
        if (duplicate.isPresent()) {
            return BanEventResponse.from(duplicate.get());
        }

        BanEvent event = new BanEvent(
            request.clientEventId(),
            normalizeClientType(request.clientType()),
            username,
            safe(request.displayName()),
            safe(request.reason()),
            joinKeywords(request.matchedKeywords()),
            joinConfiguredKeywords(request.configuredKeywords()),
            content,
            pageUrl,
            request.blockedAt() == null ? now : request.blockedAt(),
            now
        );

        BanEventResponse saved = BanEventResponse.from(repository.saveAndFlush(event));
        keywordAnalytics.record(
            saved.id(),
            saved.username(),
            request.matchedKeywords()
        );
        mentionAnalytics.record(saved.id(), request.content());
        stream.publish(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public BanPageResponse list(int page, int size, String query) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        var pageable = PageRequest.of(
            safePage,
            safeSize,
            Sort.by(Sort.Direction.DESC, "blockedAt", "id")
        );
        Page<BanEvent> result = query == null || query.isBlank()
            ? repository.findAll(pageable)
            : repository.findByUsernameContainingIgnoreCase(query.trim(), pageable);
        return new BanPageResponse(
            result.getContent().stream().map(BanEventResponse::from).toList(),
            result.getTotalElements(),
            result.getNumber(),
            result.getSize(),
            result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public BanStatsResponse stats() {
        Instant startOfToday = LocalDate.now()
            .atStartOfDay(BUSINESS_ZONE)
            .toInstant();
        return new BanStatsResponse(
            repository.count(),
            repository.countByBlockedAtGreaterThanEqual(startOfToday)
        );
    }

    private String cleanUsername(String username) {
        return username.trim().replaceFirst("^@", "");
    }

    private String normalizeClientType(String value) {
        return APP.equalsIgnoreCase(value == null ? "" : value.trim()) ? APP : "plugin";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String joinKeywords(java.util.List<String> keywords) {
        if (keywords == null) {
            return "";
        }
        return keywords.stream()
            .filter(keyword -> keyword != null && !keyword.isBlank())
            .map(String::trim)
            .distinct()
            .limit(30)
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
    }

    private String joinConfiguredKeywords(java.util.List<String> keywords) {
        if (keywords == null) {
            return "";
        }
        return keywords.stream()
            .filter(keyword -> keyword != null && !keyword.isBlank())
            .map(String::trim)
            .distinct()
            .limit(1000)
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
    }
}
