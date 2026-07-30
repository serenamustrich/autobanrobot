package com.autobanrobot.server.keyword;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "ban_event_keyword",
    indexes = {
        @Index(name = "idx_ban_keyword_keyword", columnList = "keyword"),
        @Index(name = "idx_ban_keyword_matched", columnList = "matched")
    },
    uniqueConstraints = @UniqueConstraint(
        name = "uk_ban_event_keyword",
        columnNames = {"ban_event_id", "keyword"}
    )
)
public class BanEventKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ban_event_id", nullable = false)
    private Long banEventId;

    @Column(nullable = false, length = 100)
    private String keyword;

    @Column(nullable = false)
    private boolean matched;

    @Column(nullable = false, length = 64)
    private String username;

    protected BanEventKeyword() {
    }

    public BanEventKeyword(Long banEventId, String keyword, boolean matched, String username) {
        this.banEventId = banEventId;
        this.keyword = keyword;
        this.matched = matched;
        this.username = username;
    }
}
