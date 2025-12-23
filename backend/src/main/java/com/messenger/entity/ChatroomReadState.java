package com.messenger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "chatroom_read_states",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_chatroom_user",
            columnNames = {"chatroom_id", "user_id"}
        )
    },
    indexes = {
        @Index(name = "idx_chatroom_read_chatroom_id", columnList = "chatroom_id"),
        @Index(name = "idx_chatroom_read_user_id", columnList = "user_id")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatroomReadState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chatroom_id", nullable = false)
    private Long chatroomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
