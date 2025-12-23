package com.messenger.dto.chatroom;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatroomMessageResponse {
    private Long id;
    private Long chatroomId;
    private Long userId;
    private String userName;
    private String userAvatarUrl;
    private String content;
    private Boolean isEdited;
    private List<MentionInfo> mentions;
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
