package com.messenger.websocket;

import com.messenger.dto.chatroom.ChatroomMessageResponse;
import com.messenger.dto.chatroom.SendChatroomMessageRequest;
import com.messenger.dto.message.MessageResponse;
import com.messenger.dto.message.SendMessageRequest;
import com.messenger.service.ChatroomMessageService;
import com.messenger.service.MessageService;
import com.messenger.dto.websocket.TypingIndicator;
import com.messenger.dto.websocket.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;
    private final ChatroomMessageService chatroomMessageService;
    private final WebSocketMessagePublisher messagePublisher;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload SendMessageRequest request, Principal principal) {
        try {
            Long userId = Long.parseLong(principal.getName());

            // Save message to database
            MessageResponse messageResponse = messageService.sendMessage(userId, request);

            // Publish to Redis for distribution to all instances
            messagePublisher.publishMessage(messageResponse);

        } catch (Exception e) {
            log.error("Error sending message", e);
        }
    }

    @MessageMapping("/chat.typing")
    public void sendTypingIndicator(@Payload TypingIndicator typingIndicator, Principal principal) {
        try {
            Long userId = Long.parseLong(principal.getName());
            typingIndicator.setUserId(userId);

            // Broadcast typing indicator to channel
            messagingTemplate.convertAndSend(
                    "/topic/channel/" + typingIndicator.getChannelId() + "/typing",
                    typingIndicator
            );

        } catch (Exception e) {
            log.error("Error sending typing indicator", e);
        }
    }

    // Called by Redis subscriber to broadcast messages
    public void broadcastMessage(MessageResponse message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);

        WebSocketMessage wsMessage = WebSocketMessage.builder()
                .type("MESSAGE")
                .workspaceId(message.getWorkspaceId())
                .channelId(message.getChannelId())
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .build();

        // Broadcast to all users in the channel
        messagingTemplate.convertAndSend(
                "/topic/channel/" + message.getChannelId(),
                wsMessage
        );

        // Send mention notifications
        if (message.getMentions() != null && !message.getMentions().isEmpty()) {
            for (MessageResponse.MentionInfo mention : message.getMentions()) {
                if (mention.getUserId() != null) {
                    Map<String, Object> mentionPayload = new HashMap<>();
                    mentionPayload.put("message", message);
                    mentionPayload.put("mentionType", mention.getMentionType());

                    WebSocketMessage mentionWsMessage = WebSocketMessage.builder()
                            .type("MENTION")
                            .workspaceId(message.getWorkspaceId())
                            .channelId(message.getChannelId())
                            .payload(mentionPayload)
                            .timestamp(LocalDateTime.now())
                            .build();

                    messagingTemplate.convertAndSendToUser(
                            String.valueOf(mention.getUserId()),
                            "/queue/mentions",
                            mentionWsMessage
                    );
                }
            }
        }
    }

    // Chatroom message handlers
    @MessageMapping("/chatroom.sendMessage")
    public void sendChatroomMessage(@Payload SendChatroomMessageRequest request, Principal principal) {
        try {
            Long userId = Long.parseLong(principal.getName());

            // Save message to database
            ChatroomMessageResponse messageResponse = chatroomMessageService.sendMessage(userId, request);

            // Broadcast to chatroom members
            broadcastChatroomMessage(messageResponse);

        } catch (Exception e) {
            log.error("Error sending chatroom message", e);
        }
    }

    @MessageMapping("/chatroom.typing")
    public void sendChatroomTypingIndicator(@Payload TypingIndicator typingIndicator, Principal principal) {
        try {
            Long userId = Long.parseLong(principal.getName());
            typingIndicator.setUserId(userId);

            // Broadcast typing indicator to chatroom
            messagingTemplate.convertAndSend(
                    "/topic/chatroom/" + typingIndicator.getChannelId() + "/typing",
                    typingIndicator
            );

        } catch (Exception e) {
            log.error("Error sending chatroom typing indicator", e);
        }
    }

    // Broadcast chatroom messages
    public void broadcastChatroomMessage(ChatroomMessageResponse message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);

        WebSocketMessage wsMessage = WebSocketMessage.builder()
                .type("CHATROOM_MESSAGE")
                .chatroomId(message.getChatroomId())
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .build();

        // Broadcast to all users in the chatroom
        messagingTemplate.convertAndSend(
                "/topic/chatroom/" + message.getChatroomId(),
                wsMessage
        );

        // Send mention notifications
        if (message.getMentions() != null && !message.getMentions().isEmpty()) {
            for (ChatroomMessageResponse.MentionInfo mention : message.getMentions()) {
                if (mention.getUserId() != null) {
                    Map<String, Object> mentionPayload = new HashMap<>();
                    mentionPayload.put("message", message);
                    mentionPayload.put("mentionType", mention.getMentionType());

                    WebSocketMessage mentionWsMessage = WebSocketMessage.builder()
                            .type("CHATROOM_MENTION")
                            .chatroomId(message.getChatroomId())
                            .payload(mentionPayload)
                            .timestamp(LocalDateTime.now())
                            .build();

                    messagingTemplate.convertAndSendToUser(
                            String.valueOf(mention.getUserId()),
                            "/queue/mentions",
                            mentionWsMessage
                    );
                }
            }
        }
    }
}
