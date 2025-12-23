package com.messenger.event;

import lombok.Getter;

/**
 * 메시지 저장 후 chunk_id를 업데이트하기 위한 이벤트
 */
@Getter
public class ChunkIdUpdateEvent {
    private final Long messageId;
    private final String chunkId;

    public ChunkIdUpdateEvent(Long messageId, String chunkId) {
        this.messageId = messageId;
        this.chunkId = chunkId;
    }
}
