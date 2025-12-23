package com.messenger.dto.channel;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateDirectMessageRequest {

    @NotNull(message = "Workspace ID is required")
    private Long workspaceId;

    @NotNull(message = "Target user ID is required")
    private Long targetUserId;
}
