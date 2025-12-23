package com.messenger.dto.message;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateReadStateRequest {

    @NotNull(message = "Channel ID is required")
    private Long channelId;

    @NotNull(message = "Message ID is required")
    private Long lastReadMessageId;
}
