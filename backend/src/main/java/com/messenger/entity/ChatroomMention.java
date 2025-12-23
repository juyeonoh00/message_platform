package com.messenger.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "chatroom_mentions",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_chatroom_message_user_type",
            columnNames = {"chatroom_message_id", "mentioned_user_id", "mention_type"}
        )
    },
    indexes = {
        @Index(name = "idx_chatroom_mentioned_user_read", columnList = "mentioned_user_id,is_read"),
        @Index(name = "idx_chatroom_message_id", columnList = "chatroom_message_id"),
        @Index(name = "idx_chatroom_mention_type", columnList = "mention_type")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatroomMention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatroom_message_id", nullable = false)
    private ChatroomMessage chatroomMessage;

    @Column(name = "mentioned_user_id")
    private Long mentionedUserId; // null for @here

    @Column(nullable = false, length = 20)
    private String mentionType; // user, here

    @Column(nullable = false)
    private Boolean isRead = false;
}
