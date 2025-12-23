package com.messenger.controller;

import com.messenger.dto.chatroom.ChatroomMessageResponse;
import com.messenger.dto.chatroom.SendChatroomMessageRequest;
import com.messenger.dto.chatroom.UpdateChatroomReadStateRequest;
import com.messenger.dto.message.UpdateMessageRequest;
import com.messenger.service.ChatroomMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chatroom-messages")
@RequiredArgsConstructor
public class ChatroomMessageController {

    private final ChatroomMessageService chatroomMessageService;

    @PostMapping
    public ResponseEntity<ChatroomMessageResponse> sendMessage(
            Authentication authentication,
            @Valid @RequestBody SendChatroomMessageRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        ChatroomMessageResponse response = chatroomMessageService.sendMessage(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/chatroom/{chatroomId}")
    public ResponseEntity<List<ChatroomMessageResponse>> getChatroomMessages(
            Authentication authentication,
            @PathVariable Long chatroomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long userId = (Long) authentication.getPrincipal();
        List<ChatroomMessageResponse> messages = chatroomMessageService.getChatroomMessages(userId, chatroomId, page, size);
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/read")
    public ResponseEntity<Void> updateReadState(
            Authentication authentication,
            @Valid @RequestBody UpdateChatroomReadStateRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        chatroomMessageService.updateReadState(userId, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{messageId}")
    public ResponseEntity<ChatroomMessageResponse> updateMessage(
            Authentication authentication,
            @PathVariable Long messageId,
            @Valid @RequestBody UpdateMessageRequest request) {
        Long userId = (Long) authentication.getPrincipal();

        ChatroomMessageResponse response = chatroomMessageService.updateMessage(userId, messageId, request.getContent());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            Authentication authentication,
            @PathVariable Long messageId) {
        Long userId = (Long) authentication.getPrincipal();
        chatroomMessageService.deleteMessage(userId, messageId);
        return ResponseEntity.ok().build();
    }
}
