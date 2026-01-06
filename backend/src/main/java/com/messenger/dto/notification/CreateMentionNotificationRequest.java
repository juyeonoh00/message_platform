package com.messenger.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMentionNotificationRequest {

    private Long userId;
    private Long workspaceId;
    private Long channelId;
    private Long chatroomId;
    private Long messageId;
    private Long senderId;
    private String senderName;
    private String senderAvatarUrl;
    private String messageContent;
    private String mentionType;
}
