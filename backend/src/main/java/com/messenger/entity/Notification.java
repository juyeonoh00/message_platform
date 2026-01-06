package com.messenger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_user_workspace", columnList = "user_id,workspace_id"),
    @Index(name = "idx_user_read_created", columnList = "user_id,is_read,created_at"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId; // 알림을 받을 사용자

    @Column(nullable = false)
    private Long workspaceId;

    @Column(nullable = false)
    private String type; // "mention", "reply", "reaction", etc.

    @Lob
    @Column(nullable = false)
    private String content; // 알림 내용

    private Long channelId;
    private Long chatroomId;
    private Long messageId;

    @Column(nullable = false)
    private Long senderId; // 알림을 발생시킨 사용자

    private String senderName; // 알림 발송자 이름
    private String senderAvatarUrl; // 알림 발송자 아바타

    @Column(nullable = false)
    private Boolean isRead = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
