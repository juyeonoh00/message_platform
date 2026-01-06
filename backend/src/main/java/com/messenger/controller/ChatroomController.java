package com.messenger.controller;

import com.messenger.dto.chatroom.ChatroomResponse;
import com.messenger.dto.chatroom.CreateChatroomRequest;
import com.messenger.service.ChatroomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chatrooms")
@RequiredArgsConstructor
public class ChatroomController {

    private final ChatroomService chatroomService;

    @PostMapping
    public ResponseEntity<ChatroomResponse> createChatroom(
            Authentication authentication,
            @Valid @RequestBody CreateChatroomRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        ChatroomResponse response = chatroomService.createChatroom(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ChatroomResponse>> getChatroomsByUser(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<ChatroomResponse> chatrooms = chatroomService.getChatroomsByUser(userId);
        return ResponseEntity.ok(chatrooms);
    }

    @GetMapping("/{chatroomId}")
    public ResponseEntity<ChatroomResponse> getChatroomById(
            Authentication authentication,
            @PathVariable Long chatroomId) {
        Long userId = (Long) authentication.getPrincipal();
        ChatroomResponse response = chatroomService.getChatroomById(userId, chatroomId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{chatroomId}")
    public ResponseEntity<Void> deleteChatroom(
            Authentication authentication,
            @PathVariable Long chatroomId) {
        Long userId = (Long) authentication.getPrincipal();
        chatroomService.deleteChatroom(userId, chatroomId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{chatroomId}/hide")
    public ResponseEntity<Void> hideChatroom(
            Authentication authentication,
            @PathVariable Long chatroomId) {
        Long userId = (Long) authentication.getPrincipal();
        chatroomService.hideChatroom(userId, chatroomId);
        return ResponseEntity.ok().build();
    }
}
