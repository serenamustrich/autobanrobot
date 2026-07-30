package com.autobanrobot.server.keyword;

public record KeywordRankingResponse(
    int rank,
    String keyword,
    long configuredCount,
    long hitCount,
    long banAccountCount
) {
}
