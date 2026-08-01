package com.autobanrobot.server.popular;

import com.autobanrobot.server.keyword.KeywordAnalyticsService;
import com.autobanrobot.server.keyword.KeywordRankingResponse;
import com.autobanrobot.server.mention.MentionAnalyticsService;
import com.autobanrobot.server.mention.MentionRankingResponse;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PopularTermServiceTest {

    @Test
    void returnsAllKeywordsAndMentionedAccountsWithoutTopFiftyCap() {
        KeywordAnalyticsService keywords = mock(KeywordAnalyticsService.class);
        MentionAnalyticsService mentions = mock(MentionAnalyticsService.class);
        when(keywords.allRanking()).thenReturn(IntStream.range(0, 60)
            .mapToObj(index -> new KeywordRankingResponse(
                index + 1,
                "keyword-" + index,
                100 - index
            ))
            .toList());
        when(mentions.allRanking()).thenReturn(IntStream.range(0, 60)
            .mapToObj(index -> new MentionRankingResponse(
                index + 1,
                "target" + index,
                80 - index
            ))
            .toList());

        var result = new PopularTermService(keywords, mentions).ranking();

        assertEquals(120, result.size());
        assertEquals(120, result.getLast().rank());
    }
}
