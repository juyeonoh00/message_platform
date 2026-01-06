package com.messenger.websocket;

import com.messenger.dto.message.MessageResponse;
import com.messenger.dto.notification.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketMessagePublisher {

    private final RedisTemplate<String, MessageResponse> messageRedisTemplate;
    private final ChannelTopic messageTopic;
    private final SimpMessagingTemplate messagingTemplate;

    public void publishMessage(MessageResponse message) {
        try {
            log.debug("Publishing message to Redis: channelId={}, messageId={}",
                    message.getChannelId(), message.getId());
            messageRedisTemplate.convertAndSend(messageTopic.getTopic(), message);
        } catch (Exception e) {
            log.error("Error publishing message to Redis", e);
        }
    }

    public void publishNotification(Long workspaceId, Long userId, NotificationResponse notification) {
        try {
            String destination = String.format("/topic/workspace/%d/user/%d/notifications", workspaceId, userId);
            log.debug("Publishing notification to WebSocket: workspaceId={}, userId={}, destination={}",
                    workspaceId, userId, destination);
            messagingTemplate.convertAndSend(destination, notification);
        } catch (Exception e) {
            log.error("Error publishing notification to WebSocket", e);
        }
    }
}
