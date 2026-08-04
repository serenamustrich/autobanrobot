package com.autobanrobot.server.ban;

import com.autobanrobot.server.keyword.KeywordAnalyticsService;
import com.autobanrobot.server.mention.MentionAnalyticsService;
import com.autobanrobot.server.account.Account;
import com.autobanrobot.server.account.AccountContributionRepository;
import com.autobanrobot.server.account.AccountService;
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
    private final AccountService accounts;
    private final AccountContributionRepository contributions;

    public BanEventService(
        BanEventRepository repository,
        BanEventStream stream,
        KeywordAnalyticsService keywordAnalytics,
        MentionAnalyticsService mentionAnalytics,
        AccountService accounts,
        AccountContributionRepository contributions
    ) {
        this.repository = repository;
        this.stream = stream;
        this.keywordAnalytics = keywordAnalytics;
        this.mentionAnalytics = mentionAnalytics;
        this.accounts = accounts;
        this.contributions = contributions;
    }

    @Transactional
    public BanEventResponse receive(BanEventRequest request, String authorization) {
        var existing = repository.findByClientEventId(request.clientEventId());
        if (existing.isPresent()) {
            return BanEventResponse.from(existing.get());
        }

        Instant now = Instant.now();
        String username = cleanUsername(request.username());
        String pageUrl = safe(request.pageUrl());
        String content = safe(request.content());
        Account account = accountFor(authorization, request.installationId());
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
            optionalIdentifier(request.installationId()),
            account == null ? null : account.getId(),
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

        BanEvent persisted = repository.saveAndFlush(event);
        if (account != null) contributions.insertIgnore(account.getId(), username, persisted.getId(), now);
        BanEventResponse saved = BanEventResponse.from(persisted);
        keywordAnalytics.record(
            saved.id(),
            saved.username(),
            request.matchedKeywords()
        );
        mentionAnalytics.record(saved.id(), request.content());
        stream.publish(saved);
        return saved;
    }

    @Transactional
    public void claimHistoricalEvents(Account account, String installationId) {
        for (BanEvent event : repository.findByInstallationIdAndAccountIdIsNull(installationId)) {
            event.assignAccount(account.getId());
            contributions.insertIgnore(account.getId(), event.getUsername(), event.getId(), Instant.now());
        }
    }

    private Account accountFor(String authorization, String installationId) {
        if (authorization != null && !authorization.isBlank()) return accounts.requireAccount(authorization);
        return installationId == null || installationId.isBlank() ? null : accounts.findByInstallationId(installationId.trim());
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

    private String optionalIdentifier(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
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
