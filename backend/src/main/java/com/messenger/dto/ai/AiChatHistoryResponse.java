package com.messenger.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatHistoryResponse {

    private Long id;

    private String role; // "user" or "ai"

    private String content;

    private LocalDateTime createdAt;
}
