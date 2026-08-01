package com.autobanrobot.server.mention;

import com.autobanrobot.server.ban.BanEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Service
public class MentionAnalyticsService {

    private static final Pattern MENTION_PATTERN =
        Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]{1,15})");

    private final BanEventMentionRepository repository;
    private final BanEventRepository banEventRepository;

    public MentionAnalyticsService(
        BanEventMentionRepository repository,
        BanEventRepository banEventRepository
    ) {
        this.repository = repository;
        this.banEventRepository = banEventRepository;
    }

    @Transactional
    public void record(Long banEventId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        var matcher = MENTION_PATTERN.matcher(content);
        Map<String, Integer> occurrenceCounts = new HashMap<>();
        List<BanEventMention> mentions = new ArrayList<>();
        while (matcher.find()) {
            String username = matcher.group(1).toLowerCase(Locale.ROOT);
            int occurrenceIndex = occurrenceCounts.merge(username, 1, Integer::sum);
            mentions.add(new BanEventMention(
                banEventId,
                username,
                occurrenceIndex
            ));
        }
        if (!mentions.isEmpty()) {
            repository.saveAll(mentions);
        }
    }

    @Transactional
    public void backfill() {
        banEventRepository.findAll().stream()
            .filter(event -> !repository.existsByBanEventId(event.getId()))
            .forEach(event -> record(event.getId(), event.getContent()));
    }

    @Transactional(readOnly = true)
    public List<MentionRankingResponse> ranking(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        List<MentionRankingRow> rows =
            repository.findRanking(PageRequest.of(0, safeLimit));
        return IntStream.range(0, rows.size())
            .mapToObj(index -> new MentionRankingResponse(
                index + 1,
                rows.get(index).getUsername(),
                rows.get(index).getMentionCount()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<MentionRankingResponse> allRanking() {
        List<MentionRankingRow> rows = repository.findAllRanking();
        return IntStream.range(0, rows.size())
            .mapToObj(index -> new MentionRankingResponse(
                index + 1,
                rows.get(index).getUsername(),
                rows.get(index).getMentionCount()
            ))
            .toList();
    }
}
