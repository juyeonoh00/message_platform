package com.messenger.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {

    @NotBlank(message = "Question is required")
    private String question;

    private Integer topK;  // Optional, default will be used if null

    private String llmType;  // Optional, default will be used if null
}
