package com.autobanrobot.server.mention;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BanEventMentionRepository
    extends JpaRepository<BanEventMention, Long> {

    boolean existsByBanEventId(Long banEventId);

    @Query("""
        select
            mention.mentionedUsername as username,
            count(mention.id) as mentionCount
        from BanEventMention mention
        group by mention.mentionedUsername
        order by count(mention.id) desc, mention.mentionedUsername asc
        """)
    List<MentionRankingRow> findRanking(Pageable pageable);
}
