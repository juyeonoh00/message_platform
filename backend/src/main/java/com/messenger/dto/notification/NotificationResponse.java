package com.messenger.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private Long userId;
    private Long workspaceId;
    private String type;
    private String content;
    private Long channelId;
    private Long chatroomId;
    private Long messageId;
    private Long senderId;
    private String senderName;
    private String senderAvatarUrl;
    private Boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
