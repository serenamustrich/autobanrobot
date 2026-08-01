package com.autobanrobot.server.popular;

import com.autobanrobot.server.keyword.KeywordAnalyticsService;
import com.autobanrobot.server.mention.MentionAnalyticsService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
public class PopularTermService {

    private final KeywordAnalyticsService keywordAnalytics;
    private final MentionAnalyticsService mentionAnalytics;

    public PopularTermService(
        KeywordAnalyticsService keywordAnalytics,
        MentionAnalyticsService mentionAnalytics
    ) {
        this.keywordAnalytics = keywordAnalytics;
        this.mentionAnalytics = mentionAnalytics;
    }

    public List<PopularTermResponse> ranking() {
        Map<String, PopularTermResponse> terms = new HashMap<>();
        keywordAnalytics.allRanking().forEach(item -> terms.put(
            item.keyword(),
            new PopularTermResponse(
                0,
                item.keyword(),
                "keyword_hit",
                item.hitCount()
            )
        ));
        mentionAnalytics.allRanking().forEach(item -> {
            String term = "@" + item.username();
            terms.merge(
                term,
                new PopularTermResponse(0, term, "mention", item.mentionCount()),
                (existing, mention) -> new PopularTermResponse(
                    0,
                    term,
                    "keyword_and_mention",
                    Math.max(existing.count(), mention.count())
                )
            );
        });

        List<PopularTermResponse> combined = terms.values().stream()
            .sorted(
                Comparator.comparingLong(PopularTermResponse::count)
                    .reversed()
                    .thenComparing(PopularTermResponse::term)
            )
            .toList();

        return IntStream.range(0, combined.size())
            .mapToObj(index -> {
                PopularTermResponse item = combined.get(index);
                return new PopularTermResponse(
                    index + 1,
                    item.term(),
                    item.source(),
                    item.count()
                );
            })
            .toList();
    }
}
