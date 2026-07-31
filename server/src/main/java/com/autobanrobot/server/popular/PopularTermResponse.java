package com.autobanrobot.server.popular;

public record PopularTermResponse(
    int rank,
    String term,
    String source,
    long count
) {
}
