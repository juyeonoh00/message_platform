package com.messenger.controller;

import com.messenger.dto.message.*;
import com.messenger.service.MessageService;
import com.messenger.service.MessageReactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final MessageReactionService reactionService;

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(
            Authentication authentication,
            @Valid @RequestBody SendMessageRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        MessageResponse response = messageService.sendMessage(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/channel/{channelId}")
    public ResponseEntity<List<MessageResponse>> getChannelMessages(
            Authentication authentication,
            @PathVariable Long channelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long userId = (Long) authentication.getPrincipal();
        List<MessageResponse> messages = messageService.getChannelMessages(userId, channelId, page, size);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/thread/{parentMessageId}")
    public ResponseEntity<List<MessageResponse>> getThreadReplies(
            Authentication authentication,
            @PathVariable Long parentMessageId) {
        Long userId = (Long) authentication.getPrincipal();
        List<MessageResponse> replies = messageService.getThreadReplies(userId, parentMessageId);
        return ResponseEntity.ok(replies);
    }

    @PostMapping("/read")
    public ResponseEntity<Void> updateReadState(
            Authentication authentication,
            @Valid @RequestBody UpdateReadStateRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        messageService.updateReadState(userId, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{messageId}")
    public ResponseEntity<MessageResponse> updateMessage(
            Authentication authentication,
            @PathVariable Long messageId,
            @Valid @RequestBody UpdateMessageRequest request) {
        Long userId = (Long) authentication.getPrincipal();

        MessageResponse response = messageService.updateMessage(userId, messageId, request.getContent());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            Authentication authentication,
            @PathVariable Long messageId) {
        Long userId = (Long) authentication.getPrincipal();
        messageService.deleteMessage(userId, messageId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{messageId}/reactions")
    public ResponseEntity<MessageReactionResponse> toggleReaction(
            Authentication authentication,
            @PathVariable Long messageId,
            @Valid @RequestBody AddReactionRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        MessageReactionResponse response = reactionService.toggleReaction(messageId, userId, request.getEmoji());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{messageId}/reactions")
    public ResponseEntity<List<MessageReactionResponse>> getReactions(
            @PathVariable Long messageId) {
        List<MessageReactionResponse> reactions = reactionService.getReactionsByMessageId(messageId);
        return ResponseEntity.ok(reactions);
    }

    @PostMapping("/reactions/bulk")
    public ResponseEntity<Map<Long, List<MessageReactionResponse>>> getReactionsBulk(
            @RequestBody List<Long> messageIds) {
        Map<Long, List<MessageReactionResponse>> reactions = reactionService.getReactionsByMessageIds(messageIds);
        return ResponseEntity.ok(reactions);
    }
}
