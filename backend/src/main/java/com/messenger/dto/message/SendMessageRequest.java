package com.messenger.dto.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SendMessageRequest {

    @NotNull(message = "Workspace ID is required")
    private Long workspaceId;

    @NotNull(message = "Channel ID is required")
    private Long channelId;

    @NotBlank(message = "Content is required")
    private String content;

    private Long parentMessageId; // For thread replies

    private List<Long> mentionedUserIds;
    private List<String> mentionTypes; // user, channel, here
}
