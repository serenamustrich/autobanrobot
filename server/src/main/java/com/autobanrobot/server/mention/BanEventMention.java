package com.autobanrobot.server.mention;

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
    name = "ban_event_mention",
    indexes = @Index(
        name = "idx_ban_mention_username",
        columnList = "mentioned_username"
    ),
    uniqueConstraints = @UniqueConstraint(
        name = "uk_ban_event_mention",
        columnNames = {"ban_event_id", "mentioned_username", "occurrence_index"}
    )
)
public class BanEventMention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ban_event_id", nullable = false)
    private Long banEventId;

    @Column(name = "mentioned_username", nullable = false, length = 15)
    private String mentionedUsername;

    @Column(name = "occurrence_index", nullable = false)
    private int occurrenceIndex;

    protected BanEventMention() {
    }

    public BanEventMention(
        Long banEventId,
        String mentionedUsername,
        int occurrenceIndex
    ) {
        this.banEventId = banEventId;
        this.mentionedUsername = mentionedUsername;
        this.occurrenceIndex = occurrenceIndex;
    }
}
