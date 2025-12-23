package com.messenger.dto.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateChunkRequest {

    @JsonProperty("chunk")
    private String chunk;

    @JsonProperty("channel_id")
    private Long channelId;

    @JsonProperty("thread_id")
    private Long threadId;

    @JsonProperty("document_id")
    private Long documentId;
}
