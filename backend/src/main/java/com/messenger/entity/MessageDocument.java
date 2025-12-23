package com.messenger.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageDocument {
    private String text;
    private Metadata metadata;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
        @JsonProperty("account_no")
        private String accountNo;

        @JsonProperty("channel_id")
        private Long channelId;

        @JsonProperty("chunk_id")
        private String chunkId;

        @JsonProperty("thread_id")
        private Long threadId;

        @JsonProperty("document_id")
        private Long documentId;

        @JsonProperty("user_name")
        private String userName;

        @JsonProperty("channel_name")
        private String channelName;

        @JsonProperty("created_at")
        private LocalDateTime createdAt;
    }
}