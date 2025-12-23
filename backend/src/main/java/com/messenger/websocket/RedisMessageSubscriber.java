package com.messenger.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messenger.dto.message.MessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessageSubscriber implements MessageListener {

    private final WebSocketController webSocketController;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String messageBody = new String(message.getBody());
            log.debug("Received message from Redis: {}", messageBody);

            MessageResponse messageResponse = objectMapper.readValue(messageBody, MessageResponse.class);

            // Broadcast to all WebSocket clients connected to this instance
            webSocketController.broadcastMessage(messageResponse);

        } catch (Exception e) {
            log.error("Error processing Redis message", e);
        }
    }
}
