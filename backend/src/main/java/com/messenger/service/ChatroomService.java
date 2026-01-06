package com.messenger.service;

import com.messenger.dto.chatroom.ChatroomResponse;
import com.messenger.dto.chatroom.CreateChatroomRequest;
import com.messenger.entity.*;
import com.messenger.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatroomService {

    private final ChatroomRepository chatroomRepository;
    private final ChatroomMemberRepository chatroomMemberRepository;
    private final ChatroomMessageRepository chatroomMessageRepository;
    private final UserRepository userRepository;
    private final ElasticsearchService elasticsearchService;
    private final RagApiClient ragApiClient;

    @Transactional
    public ChatroomResponse createChatroom(Long userId, CreateChatroomRequest request) {
        // Check if chatroom already exists
        Optional<Chatroom> existingChatroom = chatroomRepository.findDirectChatroomBetweenUsers(
                userId, request.getTargetUserId());

        if (existingChatroom.isPresent()) {
            Chatroom chatroom = existingChatroom.get();

            // If chatroom was hidden by current user, unhide it
            if (isHiddenByUser(chatroom, userId)) {
                unhideChatroom(chatroom, userId);
            }

            User targetUser = userRepository.findById(request.getTargetUserId())
                    .orElseThrow(() -> new RuntimeException("Target user not found"));

            return ChatroomResponse.builder()
                    .id(chatroom.getId())
                    .name(targetUser.getName())
                    .avatarUrl(targetUser.getAvatarUrl())
                    .targetUserId(targetUser.getId())
                    .createdBy(chatroom.getCreatedBy())
                    .isMember(true)
                    .unreadCount(calculateUnreadCount(userId, chatroom.getId()))
                    .createdAt(chatroom.getCreatedAt())
                    .build();
        }

        // Create new chatroom
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        User targetUser = userRepository.findById(request.getTargetUserId())
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        // Store participant usernames in name field
        String chatroomName = currentUser.getName() + ", " + targetUser.getName();

        Chatroom chatroom = Chatroom.builder()
                .name(chatroomName)
                .createdBy(userId)
                .build();

        chatroom = chatroomRepository.save(chatroom);

        // Add both users as members
        ChatroomMember member1 = ChatroomMember.builder()
                .chatroom(chatroom)
                .user(currentUser)
                .build();

        ChatroomMember member2 = ChatroomMember.builder()
                .chatroom(chatroom)
                .user(targetUser)
                .build();

        chatroomMemberRepository.save(member1);
        chatroomMemberRepository.save(member2);

        return ChatroomResponse.builder()
                .id(chatroom.getId())
                .name(targetUser.getName())
                .avatarUrl(targetUser.getAvatarUrl())
                .targetUserId(targetUser.getId())
                .createdBy(chatroom.getCreatedBy())
                .isMember(true)
                .unreadCount(0)
                .createdAt(chatroom.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ChatroomResponse> getChatroomsByUser(Long userId) {
        List<Chatroom> chatrooms = chatroomRepository.findByUserId(userId);

        return chatrooms.stream()
                .filter(chatroom -> !isHiddenByUser(chatroom, userId)) // 숨겨진 채팅방 제외
                .map(chatroom -> {
                    // Find the other user in the chatroom
                    List<ChatroomMember> members = chatroomMemberRepository.findByChatroomId(chatroom.getId());
                    User otherUser = members.stream()
                            .map(ChatroomMember::getUser)
                            .filter(u -> !u.getId().equals(userId))
                            .findFirst()
                            .orElse(null);

                    String displayName = otherUser != null ? otherUser.getName() : "Unknown";
                    String avatarUrl = otherUser != null ? otherUser.getAvatarUrl() : null;
                    Long targetUserId = otherUser != null ? otherUser.getId() : null;
                    int unreadCount = calculateUnreadCount(userId, chatroom.getId());

                    return ChatroomResponse.builder()
                            .id(chatroom.getId())
                            .name(displayName)
                            .avatarUrl(avatarUrl)
                            .targetUserId(targetUserId)
                            .createdBy(chatroom.getCreatedBy())
                            .isMember(true)
                            .unreadCount(unreadCount)
                            .createdAt(chatroom.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ChatroomResponse getChatroomById(Long userId, Long chatroomId) {
        Chatroom chatroom = chatroomRepository.findById(chatroomId)
                .orElseThrow(() -> new RuntimeException("Chatroom not found"));

        // Verify user is member of the chatroom
        if (!chatroomMemberRepository.existsByChatroomIdAndUserId(chatroomId, userId)) {
            throw new RuntimeException("You are not a member of this chatroom");
        }

        // Find the other user in the chatroom
        List<ChatroomMember> members = chatroomMemberRepository.findByChatroomId(chatroom.getId());
        User otherUser = members.stream()
                .map(ChatroomMember::getUser)
                .filter(u -> !u.getId().equals(userId))
                .findFirst()
                .orElse(null);

        String displayName = otherUser != null ? otherUser.getName() : "Unknown";
        String avatarUrl = otherUser != null ? otherUser.getAvatarUrl() : null;
        Long targetUserId = otherUser != null ? otherUser.getId() : null;
        int unreadCount = calculateUnreadCount(userId, chatroom.getId());

        return ChatroomResponse.builder()
                .id(chatroom.getId())
                .name(displayName)
                .avatarUrl(avatarUrl)
                .targetUserId(targetUserId)
                .createdBy(chatroom.getCreatedBy())
                .isMember(true)
                .unreadCount(unreadCount)
                .createdAt(chatroom.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public boolean isMember(Long userId, Long chatroomId) {
        return chatroomMemberRepository.existsByChatroomIdAndUserId(chatroomId, userId);
    }

    private int calculateUnreadCount(Long userId, Long chatroomId) {
        return 0;
    }

    @Transactional
    public void deleteChatroom(Long userId, Long chatroomId) {
        Chatroom chatroom = chatroomRepository.findById(chatroomId)
                .orElseThrow(() -> new RuntimeException("Chatroom not found"));

        // Only creator can delete
        if (!chatroom.getCreatedBy().equals(userId)) {
            throw new RuntimeException("Only the creator can delete this chatroom");
        }

        // Delete all messages from RAG API (also deletes from Elasticsearch)
        List<ChatroomMessage> messages = chatroomMessageRepository.findByChatroomId(chatroomId);
        messages.forEach(msg -> {
            if (msg.getChunkId() != null && !msg.getChunkId().isEmpty()) {
                try {
                    ragApiClient.deleteChunk(msg.getChunkId(), "message_platform");
                    log.debug("Deleted chunk from RAG API: chunkId={}", msg.getChunkId());
                } catch (Exception e) {
                    log.error("Failed to delete chunk from RAG API: chunkId={}", msg.getChunkId(), e);
                }
            }
        });

        // Delete all messages
        chatroomMessageRepository.deleteByChatroomId(chatroomId);

        // Delete all members
        chatroomMemberRepository.deleteByChatroomId(chatroomId);

        // Delete chatroom
        chatroomRepository.delete(chatroom);
    }

    @Transactional
    public void hideChatroom(Long userId, Long chatroomId) {
        Chatroom chatroom = chatroomRepository.findById(chatroomId)
                .orElseThrow(() -> new RuntimeException("Chatroom not found"));

        // Verify user is member
        if (!chatroomMemberRepository.existsByChatroomIdAndUserId(chatroomId, userId)) {
            throw new RuntimeException("You are not a member of this chatroom");
        }

        // Add user to hiddenBy list
        String hiddenBy = chatroom.getHiddenBy();
        if (hiddenBy == null || hiddenBy.isEmpty()) {
            chatroom.setHiddenBy(userId.toString());
        } else if (!hiddenBy.contains(userId.toString())) {
            chatroom.setHiddenBy(hiddenBy + "," + userId);
        }

        chatroomRepository.save(chatroom);
    }

    private void unhideChatroom(Chatroom chatroom, Long userId) {
        String hiddenBy = chatroom.getHiddenBy();
        if (hiddenBy != null && !hiddenBy.isEmpty()) {
            // Remove user from hiddenBy list
            String[] userIds = hiddenBy.split(",");
            String newHiddenBy = "";
            for (String id : userIds) {
                if (!id.trim().equals(userId.toString())) {
                    if (!newHiddenBy.isEmpty()) {
                        newHiddenBy += ",";
                    }
                    newHiddenBy += id.trim();
                }
            }
            chatroom.setHiddenBy(newHiddenBy.isEmpty() ? null : newHiddenBy);
            chatroomRepository.save(chatroom);
        }
    }

    private boolean isHiddenByUser(Chatroom chatroom, Long userId) {
        String hiddenBy = chatroom.getHiddenBy();
        if (hiddenBy == null || hiddenBy.isEmpty()) {
            return false;
        }
        String[] userIds = hiddenBy.split(",");
        for (String id : userIds) {
            if (id.trim().equals(userId.toString())) {
                return true;
            }
        }
        return false;
    }
}
