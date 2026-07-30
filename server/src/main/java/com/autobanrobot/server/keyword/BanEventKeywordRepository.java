package com.autobanrobot.server.keyword;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BanEventKeywordRepository extends JpaRepository<BanEventKeyword, Long> {

    @Query("""
        select
            keyword.keyword as keyword,
            count(keyword.id) as configuredCount,
            sum(case when keyword.matched = true then 1 else 0 end) as hitCount,
            count(distinct case when keyword.matched = true then keyword.username else null end)
                as banAccountCount
        from BanEventKeyword keyword
        group by keyword.keyword
        order by count(keyword.id) desc,
            sum(case when keyword.matched = true then 1 else 0 end) desc,
            keyword.keyword asc
        """)
    List<KeywordRankingRow> findRanking(Pageable pageable);
}
