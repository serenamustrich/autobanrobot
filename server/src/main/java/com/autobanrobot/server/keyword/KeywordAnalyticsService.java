package com.autobanrobot.server.keyword;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

@Service
public class KeywordAnalyticsService {

    private final BanEventKeywordRepository repository;

    public KeywordAnalyticsService(BanEventKeywordRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(
        Long banEventId,
        String username,
        List<String> matchedKeywords
    ) {
        Set<String> matched = normalize(matchedKeywords);
        repository.saveAll(matched.stream()
            .map(keyword -> new BanEventKeyword(
                banEventId,
                keyword,
                true,
                username
            ))
            .toList());
    }

    @Transactional(readOnly = true)
    public List<KeywordRankingResponse> ranking(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        List<KeywordRankingRow> rows = repository.findRanking(PageRequest.of(0, safeLimit));
        return IntStream.range(0, rows.size())
            .mapToObj(index -> {
                KeywordRankingRow row = rows.get(index);
                return new KeywordRankingResponse(
                    index + 1,
                    row.getKeyword(),
                    row.getHitCount()
                );
            })
            .toList();
    }

    private Set<String> normalize(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .limit(1000)
            .forEach(result::add);
        return result;
    }
}
