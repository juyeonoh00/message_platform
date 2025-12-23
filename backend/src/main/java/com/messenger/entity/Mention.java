package com.messenger.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mentions",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_message_user_type",
            columnNames = {"message_id", "mentioned_user_id", "mention_type"}
        )
    },
    indexes = {
        @Index(name = "idx_mentioned_user_read", columnList = "mentioned_user_id,is_read"),
        @Index(name = "idx_message_id", columnList = "message_id"),
        @Index(name = "idx_mention_type", columnList = "mention_type")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(name = "mentioned_user_id")
    private Long mentionedUserId; // null for @channel, @here

    @Column(nullable = false, length = 20)
    private String mentionType; // user, channel, here

    @Column(nullable = false)
    private Boolean isRead = false;
}
