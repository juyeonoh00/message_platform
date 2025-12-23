package com.messenger.dto.chatroom;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SendChatroomMessageRequest {

    @NotNull(message = "Workspace ID is required")
    private Long workspaceId;

    @NotNull(message = "Chatroom ID is required")
    private Long chatroomId;

    @NotBlank(message = "Content is required")
    private String content;

    private List<Long> mentionedUserIds;
    private List<String> mentionTypes; // user, here
}
