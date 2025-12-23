package com.messenger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "read_states",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_channel_user",
            columnNames = {"channel_id", "user_id"}
        )
    },
    indexes = {
        @Index(name = "idx_channel_id", columnList = "channel_id"),
        @Index(name = "idx_user_id", columnList = "user_id")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReadState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
