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
public class ChunkResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("chunk_id")
    private String chunkId;

    @JsonProperty("message")
    private String message;
}
