package com.messenger.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {
    private String type; // MESSAGE, MESSAGE_UPDATED, MESSAGE_DELETED, TYPING, READ_STATE, MENTION, USER_STATUS, CHATROOM_MESSAGE, CHATROOM_MENTION
    private Long workspaceId;
    private Long channelId;
    private Long chatroomId;
    private Map<String, Object> payload;
    private LocalDateTime timestamp;
}
