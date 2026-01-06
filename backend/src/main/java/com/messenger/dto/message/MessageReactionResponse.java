package com.messenger.dto.message;

import com.messenger.entity.MessageReaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageReactionResponse {
    private Long id;
    private Long messageId;
    private Long userId;
    private String emoji;
    private LocalDateTime createdAt;

    public static MessageReactionResponse from(MessageReaction reaction) {
        return MessageReactionResponse.builder()
                .id(reaction.getId())
                .messageId(reaction.getMessage().getId())
                .userId(reaction.getUser().getId())
                .emoji(reaction.getEmoji())
                .createdAt(reaction.getCreatedAt())
                .build();
    }
}
