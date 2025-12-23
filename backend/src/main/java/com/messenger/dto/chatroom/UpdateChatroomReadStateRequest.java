package com.messenger.dto.chatroom;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateChatroomReadStateRequest {

    @NotNull(message = "Chatroom ID is required")
    private Long chatroomId;

    @NotNull(message = "Last read message ID is required")
    private Long lastReadMessageId;
}
