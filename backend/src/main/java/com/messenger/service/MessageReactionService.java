package com.messenger.service;

import com.messenger.dto.message.MessageReactionResponse;
import com.messenger.entity.MessageReaction;
import com.messenger.repository.MessageReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageReactionService {

    private final MessageReactionRepository reactionRepository;

    @Transactional
    public MessageReactionResponse toggleReaction(Long messageId, Long userId, String emoji) {
        var existingReaction = reactionRepository.findByMessage_IdAndUser_IdAndEmoji(messageId, userId, emoji);

        if (existingReaction.isPresent()) {
            // Remove reaction
            reactionRepository.delete(existingReaction.get());
            return null; // Indicates deletion
        } else {
            // Add reaction
            MessageReaction reaction = MessageReaction.builder()
                    .message(com.messenger.entity.Message.builder().id(messageId).build())
                    .user(com.messenger.entity.User.builder().id(userId).build())
                    .emoji(emoji)
                    .build();
            MessageReaction saved = reactionRepository.save(reaction);
            return MessageReactionResponse.from(saved);
        }
    }

    @Transactional(readOnly = true)
    public List<MessageReactionResponse> getReactionsByMessageId(Long messageId) {
        return reactionRepository.findByMessage_Id(messageId)
                .stream()
                .map(MessageReactionResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<Long, List<MessageReactionResponse>> getReactionsByMessageIds(List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return new HashMap<>();
        }

        List<MessageReaction> reactions = reactionRepository.findAllByMessageIds(messageIds);

        Map<Long, List<MessageReactionResponse>> result = new HashMap<>();
        for (MessageReaction reaction : reactions) {
            result.computeIfAbsent(reaction.getMessage().getId(), k -> new java.util.ArrayList<>())
                    .add(MessageReactionResponse.from(reaction));
        }

        return result;
    }
}
