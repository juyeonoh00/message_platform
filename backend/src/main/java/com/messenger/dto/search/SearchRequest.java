package com.messenger.dto.search;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SearchRequest {

    @NotNull(message = "Workspace ID is required")
    private Long workspaceId;

    private Long channelId;

    private String keyword;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
