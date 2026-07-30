package com.autobanrobot.server.keyword;

public interface KeywordRankingRow {

    String getKeyword();

    long getConfiguredCount();

    long getHitCount();

    long getBanAccountCount();
}
