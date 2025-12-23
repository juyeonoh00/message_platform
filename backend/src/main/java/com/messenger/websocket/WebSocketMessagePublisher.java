package com.messenger.websocket;

import com.messenger.dto.message.MessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketMessagePublisher {

    private final RedisTemplate<String, MessageResponse> messageRedisTemplate;
    private final ChannelTopic messageTopic;

    public void publishMessage(MessageResponse message) {
        try {
            log.debug("Publishing message to Redis: channelId={}, messageId={}",
                    message.getChannelId(), message.getId());
            messageRedisTemplate.convertAndSend(messageTopic.getTopic(), message);
        } catch (Exception e) {
            log.error("Error publishing message to Redis", e);
        }
    }
}
