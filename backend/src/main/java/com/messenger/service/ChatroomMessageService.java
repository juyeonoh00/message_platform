package com.messenger.service;

import com.messenger.dto.chatroom.ChatroomMessageResponse;
import com.messenger.dto.chatroom.SendChatroomMessageRequest;
import com.messenger.dto.chatroom.UpdateChatroomReadStateRequest;
import com.messenger.dto.notification.CreateMentionNotificationRequest;
import com.messenger.entity.*;
import com.messenger.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatroomMessageService {

    private final ChatroomMessageRepository chatroomMessageRepository;
    private final ChatroomRepository chatroomRepository;
    private final ChatroomMemberRepository chatroomMemberRepository;
    private final UserRepository userRepository;
    private final ChatroomMentionRepository chatroomMentionRepository;
    private final ChatroomReadStateRepository chatroomReadStateRepository;
    private final NotificationService notificationService;

    @Transactional
    public ChatroomMessageResponse sendMessage(Long userId, SendChatroomMessageRequest request) {

        Chatroom chatroom = chatroomRepository.findById(request.getChatroomId())
                .orElseThrow(() -> new RuntimeException("Chatroom not found"));

        // Verify user is member of the chatroom
        if (!chatroomMemberRepository.existsByChatroomIdAndUserId(request.getChatroomId(), userId)) {
            throw new RuntimeException("Not a member of this chatroom");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ChatroomMessage message = ChatroomMessage.builder()
                .chatroomId(request.getChatroomId())
                .userId(userId)
                .content(request.getContent())
                .isEdited(false)
                .isDeleted(false)
                .build();

        message = chatroomMessageRepository.save(message);

        // TODO: Index message in Elasticsearch

        // Handle mentions
        List<ChatroomMessageResponse.MentionInfo> mentionInfos = new ArrayList<>();

        // First, handle user mentions
        if (request.getMentionedUserIds() != null && !request.getMentionedUserIds().isEmpty()) {
            for (int i = 0; i < request.getMentionedUserIds().size(); i++) {
                Long mentionedUserId = request.getMentionedUserIds().get(i);
                String mentionType = request.getMentionTypes() != null && i < request.getMentionTypes().size()
                        ? request.getMentionTypes().get(i) : "user";

                ChatroomMention mention = ChatroomMention.builder()
                        .chatroomMessage(message)
                        .mentionedUserId(mentionedUserId)
                        .mentionType(mentionType)
                        .isRead(false)
                        .build();

                chatroomMentionRepository.save(mention);

                mentionInfos.add(ChatroomMessageResponse.MentionInfo.builder()
                        .userId(mentionedUserId)
                        .mentionType(mentionType)
                        .build());

                // Create notification for mention in chatroom
                notificationService.createMentionNotification(
                        CreateMentionNotificationRequest.builder()
                                .userId(mentionedUserId)
                                .workspaceId(null)
                                .channelId(null)
                                .chatroomId(chatroom.getId())
                                .messageId(message.getId())
                                .senderId(user.getId())
                                .senderName(user.getName())
                                .senderAvatarUrl(user.getAvatarUrl())
                                .messageContent(request.getContent())
                                .mentionType(mentionType)
                                .build()
                );
            }
        }

        // Handle @everyone (channel) mentions
        if (request.getMentionTypes() != null && request.getMentionTypes().contains("channel")) {
            // Get all chatroom members
            List<ChatroomMember> chatroomMembers = chatroomMemberRepository.findByChatroomId(request.getChatroomId());

            for (ChatroomMember member : chatroomMembers) {
                // Skip the message sender
                if (member.getUser().getId().equals(userId)) {
                    continue;
                }

                ChatroomMention mention = ChatroomMention.builder()
                        .chatroomMessage(message)
                        .mentionedUserId(member.getUser().getId())
                        .mentionType("channel")
                        .isRead(false)
                        .build();

                chatroomMentionRepository.save(mention);

                mentionInfos.add(ChatroomMessageResponse.MentionInfo.builder()
                        .userId(member.getUser().getId())
                        .mentionType("channel")
                        .build());

                // Create notification for channel mention in chatroom
                notificationService.createMentionNotification(
                        CreateMentionNotificationRequest.builder()
                                .userId(member.getUser().getId())
                                .workspaceId(null)
                                .channelId(null)
                                .chatroomId(chatroom.getId())
                                .messageId(message.getId())
                                .senderId(user.getId())
                                .senderName(user.getName())
                                .senderAvatarUrl(user.getAvatarUrl())
                                .messageContent(request.getContent())
                                .mentionType("channel")
                                .build()
                );
            }
        }

        return ChatroomMessageResponse.builder()
                .id(message.getId())
                .chatroomId(message.getChatroomId())
                .userId(message.getUserId())
                .userName(user.getName())
                .userAvatarUrl(user.getAvatarUrl())
                .content(message.getContent())
                .isEdited(message.getIsEdited())
                .mentions(mentionInfos)
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ChatroomMessageResponse> getChatroomMessages(Long userId, Long chatroomId, int page, int size) {
        Chatroom chatroom = chatroomRepository.findById(chatroomId)
                .orElseThrow(() -> new RuntimeException("Chatroom not found"));

        // Verify user is member of the chatroom
        if (!chatroomMemberRepository.existsByChatroomIdAndUserId(chatroomId, userId)) {
            throw new RuntimeException("Not a member of this chatroom");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<ChatroomMessage> messages = chatroomMessageRepository.findByChatroomIdAndIsDeletedFalseOrderByCreatedAtAsc(chatroomId, pageable);

        return messages.stream()
                .map(this::toChatroomMessageResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateReadState(Long userId, UpdateChatroomReadStateRequest request) {
        Chatroom chatroom = chatroomRepository.findById(request.getChatroomId())
                .orElseThrow(() -> new RuntimeException("Chatroom not found"));

        // Verify user is member of the chatroom
        if (!chatroomMemberRepository.existsByChatroomIdAndUserId(request.getChatroomId(), userId)) {
            throw new RuntimeException("Not a member of this chatroom");
        }

        ChatroomReadState readState = chatroomReadStateRepository.findByChatroomIdAndUserId(request.getChatroomId(), userId)
                .orElse(ChatroomReadState.builder()
                        .chatroomId(request.getChatroomId())
                        .userId(userId)
                        .build());

        readState.setLastReadMessageId(request.getLastReadMessageId());
        chatroomReadStateRepository.save(readState);
    }

    private ChatroomMessageResponse toChatroomMessageResponse(ChatroomMessage message) {
        User user = userRepository.findById(message.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<ChatroomMention> mentions = chatroomMentionRepository.findByChatroomMessageId(message.getId());
        List<ChatroomMessageResponse.MentionInfo> mentionInfos = mentions.stream()
                .map(m -> ChatroomMessageResponse.MentionInfo.builder()
                        .userId(m.getMentionedUserId())
                        .mentionType(m.getMentionType())
                        .build())
                .collect(Collectors.toList());

        return ChatroomMessageResponse.builder()
                .id(message.getId())
                .chatroomId(message.getChatroomId())
                .userId(message.getUserId())
                .userName(user.getName())
                .userAvatarUrl(user.getAvatarUrl())
                .content(message.getContent())
                .isEdited(message.getIsEdited())
                .mentions(mentionInfos)
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .build();
    }

    @Transactional
    public ChatroomMessageResponse updateMessage(Long userId, Long messageId, String newContent) {
        ChatroomMessage message = chatroomMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        // Check if user owns this message
        if (!message.getUserId().equals(userId)) {
            throw new RuntimeException("Cannot edit message - not the owner");
        }

        if (message.getIsDeleted()) {
            throw new RuntimeException("Cannot edit deleted message");
        }

        message.setContent(newContent);
        message.setIsEdited(true);
        message = chatroomMessageRepository.save(message);

        return toChatroomMessageResponse(message);
    }

    @Transactional
    public void deleteMessage(Long userId, Long messageId) {
        ChatroomMessage message = chatroomMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // Check if user owns this message
        if (!message.getUserId().equals(userId)) {
            throw new RuntimeException("Cannot delete message - not the owner");
        }

        message.setIsDeleted(true);
        message.setContent("[삭제된 메시지]");
        chatroomMessageRepository.save(message);

    }
}
