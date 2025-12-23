package com.messenger.dto.channel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelResponse {
    private Long id;
    private Long workspaceId;
    private String name;
    private String description;
    private Boolean isPrivate;
    private Long createdBy;
    private Boolean isMember;
    private Integer unreadCount;
    private LocalDateTime createdAt;
}
