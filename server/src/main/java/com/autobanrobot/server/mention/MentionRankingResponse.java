package com.autobanrobot.server.mention;

public record MentionRankingResponse(
    int rank,
    String username,
    long mentionCount
) {
}
