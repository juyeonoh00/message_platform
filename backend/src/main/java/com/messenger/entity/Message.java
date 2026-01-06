package com.messenger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "channel_messages", indexes = {
    @Index(name = "idx_channel_created", columnList = "channel_id,created_at"),
    @Index(name = "idx_parent_created", columnList = "parent_message_id,created_at"),
    @Index(name = "idx_workspace_channel", columnList = "workspace_id,channel_id"),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_deleted", columnList = "is_deleted")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long workspaceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Channel channel;

    @Column(nullable = false)
    private Long userId;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(name = "parent_message_id")
    private Long parentMessageId; // For thread replies

    @Column(nullable = false)
    private Boolean isEdited = false;

    @Column(nullable = false)
    private Boolean isDeleted = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "chunk_id")
    private String chunkId; // RAG API에서 반환된 Elasticsearch chunk ID

    // For thread message count
    @Transient
    private Integer replyCount;
}
