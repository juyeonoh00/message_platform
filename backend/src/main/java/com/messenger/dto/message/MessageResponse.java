package com.messenger.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    private Long id;
    private Long workspaceId;
    private Long channelId;
    private Long userId;
    private String userName;
    private String userAvatarUrl;
    private String content;
    private Long parentMessageId;
    private Integer replyCount;
    private Boolean isEdited;
    private List<MentionInfo> mentions;
    private Map<String, List<Long>> reactions; // emoji -> list of userIds
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime editedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MentionInfo {
        private Long userId;
        private String mentionType;
    }
}
