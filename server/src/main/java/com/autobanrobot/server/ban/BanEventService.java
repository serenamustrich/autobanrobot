package com.autobanrobot.server.ban;

import com.autobanrobot.server.keyword.KeywordAnalyticsService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class BanEventService {

    private final BanEventRepository repository;
    private final BanEventStream stream;
    private final KeywordAnalyticsService keywordAnalytics;

    public BanEventService(
        BanEventRepository repository,
        BanEventStream stream,
        KeywordAnalyticsService keywordAnalytics
    ) {
        this.repository = repository;
        this.stream = stream;
        this.keywordAnalytics = keywordAnalytics;
    }

    @Transactional
    public BanEventResponse receive(BanEventRequest request) {
        var existing = repository.findByClientEventId(request.clientEventId());
        if (existing.isPresent()) {
            return BanEventResponse.from(existing.get());
        }

        Instant now = Instant.now();
        BanEvent event = new BanEvent(
            request.clientEventId(),
            cleanUsername(request.username()),
            safe(request.displayName()),
            safe(request.reason()),
            joinKeywords(request.matchedKeywords()),
            joinConfiguredKeywords(request.configuredKeywords()),
            safe(request.content()),
            safe(request.pageUrl()),
            request.blockedAt() == null ? now : request.blockedAt(),
            now
        );

        try {
            BanEventResponse saved = BanEventResponse.from(repository.saveAndFlush(event));
            keywordAnalytics.record(
                saved.id(),
                saved.username(),
                request.configuredKeywords(),
                request.matchedKeywords()
            );
            stream.publish(saved);
            return saved;
        } catch (DataIntegrityViolationException duplicate) {
            return repository.findByClientEventId(request.clientEventId())
                .map(BanEventResponse::from)
                .orElseThrow(() -> duplicate);
        }
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
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant();
        return new BanStatsResponse(
            repository.count(),
            repository.countByBlockedAtGreaterThanEqual(startOfToday)
        );
    }

    private String cleanUsername(String username) {
        return username.trim().replaceFirst("^@", "");
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
