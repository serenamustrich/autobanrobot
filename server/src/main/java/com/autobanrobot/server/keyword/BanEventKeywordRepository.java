package com.autobanrobot.server.keyword;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BanEventKeywordRepository extends JpaRepository<BanEventKeyword, Long> {

    @Query("""
        select
            keyword.keyword as keyword,
            count(keyword.id) as hitCount
        from BanEventKeyword keyword
        where keyword.matched = true
        group by keyword.keyword
        order by count(keyword.id) desc, keyword.keyword asc
        """)
    List<KeywordRankingRow> findRanking(Pageable pageable);
}
