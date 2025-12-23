package com.messenger.websocket;

import com.messenger.config.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authToken = accessor.getFirstNativeHeader("Authorization");

            if (!StringUtils.hasText(authToken) || !authToken.startsWith("Bearer ")) {
                log.error("Missing or invalid Authorization header in WebSocket connection");
                throw new IllegalArgumentException("Missing or invalid Authorization header");
            }

            String jwt = authToken.substring(7);

            if (!jwtTokenProvider.validateToken(jwt)) {
                log.error("Invalid or expired JWT token in WebSocket connection");
                throw new IllegalArgumentException("Invalid or expired JWT token");
            }

            Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
            Authentication auth = new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>());
            accessor.setUser(auth);
            log.info("WebSocket connection authenticated for user: {}", userId);
        }

        return message;
    }
}
