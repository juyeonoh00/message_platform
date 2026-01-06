package com.messenger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "chatroom_messages", indexes = {
    @Index(name = "idx_chatroom_created", columnList = "chatroom_id,created_at"),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_deleted", columnList = "is_deleted")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatroomMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long chatroomId;

    @Column(nullable = false)
    private Long userId;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private Boolean isEdited = false;

    @Column(nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "chunk_id")
    private String chunkId; // RAG API에서 반환된 Elasticsearch chunk ID

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
