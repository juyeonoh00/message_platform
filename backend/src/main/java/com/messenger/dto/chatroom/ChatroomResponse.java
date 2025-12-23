package com.messenger.dto.chatroom;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatroomResponse {

    private Long id;
    private Long workspaceId;
    private String name; // Participant usernames
    private String avatarUrl; // Other user's avatar URL
    private Long targetUserId; // Other user's ID (for opening profile)
    private Long createdBy;
    private boolean isMember;
    private int unreadCount;
    private LocalDateTime createdAt;
}
