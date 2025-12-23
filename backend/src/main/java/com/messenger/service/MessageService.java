package com.messenger.service;

import com.messenger.dto.message.*;
import com.messenger.dto.websocket.WebSocketMessage;
import com.messenger.entity.*;
import com.messenger.event.ChunkIdUpdateEvent;
import com.messenger.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final UserRepository userRepository;
    private final MentionRepository mentionRepository;
    private final ReadStateRepository readStateRepository;
    private final MessageReactionRepository reactionRepository;
    private final ElasticsearchService elasticsearchService;
    private final RagApiClient ragApiClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MessageResponse sendMessage(Long userId, SendMessageRequest request) {

        Channel channel = channelRepository.findById(request.getChannelId())
                .orElseThrow(() -> new RuntimeException("Channel not found"));

        // 비공개 채널인 경우에만 멤버십 확인
        if (channel.getIsPrivate() && !channelMemberRepository.existsByChannelIdAndUserId(request.getChannelId(), userId)) {
            throw new RuntimeException("Not a member of this channel");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Message message = Message.builder()
                .workspaceId(request.getWorkspaceId())
                .channel(channel)
                .userId(userId)
                .content(request.getContent())
                .parentMessageId(request.getParentMessageId())
                .isEdited(false)
                .isDeleted(false)
                .build();

        message = messageRepository.save(message);
        System.out.println("Message saved with ID: " + message.getId() + ", Parent ID: " + message.getParentMessageId());

        // Send to RAG API for ingestion (스레드 및 스레드 답변)
        // 트랜잭션 커밋 후 chunk_id를 업데이트하기 위해 이벤트 발행
        String chunkId = sendToRagApi(message);
        if (chunkId != null) {
            eventPublisher.publishEvent(new ChunkIdUpdateEvent(message.getId(), chunkId));
        } else {
            log.warn("RAG API에서 chunk_id를 받지 못했습니다.");
        }

        // Handle mentions
        List<MessageResponse.MentionInfo> mentionInfos = new ArrayList<>();
        if (request.getMentionedUserIds() != null && !request.getMentionedUserIds().isEmpty()) {
            for (int i = 0; i < request.getMentionedUserIds().size(); i++) {
                Long mentionedUserId = request.getMentionedUserIds().get(i);
                String mentionType = request.getMentionTypes() != null && i < request.getMentionTypes().size()
                        ? request.getMentionTypes().get(i) : "user";

                Mention mention = Mention.builder()
                        .message(message)
                        .mentionedUserId(mentionedUserId)
                        .mentionType(mentionType)
                        .isRead(false)
                        .build();

                mentionRepository.save(mention);

                mentionInfos.add(MessageResponse.MentionInfo.builder()
                        .userId(mentionedUserId)
                        .mentionType(mentionType)
                        .build());
            }
        }

        return MessageResponse.builder()
                .id(message.getId())
                .workspaceId(message.getWorkspaceId())
                .channelId(message.getChannel().getId())
                .userId(message.getUserId())
                .userName(user.getName())
                .userAvatarUrl(user.getAvatarUrl())
                .content(message.getContent())
                .parentMessageId(message.getParentMessageId())
                .replyCount(0)
                .isEdited(message.getIsEdited())
                .mentions(mentionInfos)
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .editedAt(message.getEditedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getChannelMessages(Long userId, Long channelId, int page, int size) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));

        // 비공개 채널인 경우에만 멤버십 확인
        if (channel.getIsPrivate() && !channelMemberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            throw new RuntimeException("Not a member of this channel");
        }

        Pageable pageable = PageRequest.of(page, size);
        // Get top-level messages only (exclude thread replies) - ordered by createdAt ascending (oldest first)
        Page<Message> messages = messageRepository.findByChannelIdAndParentMessageIdIsNullAndIsDeletedFalseOrderByCreatedAtAsc(channelId, pageable);
        System.out.println("Loaded " + messages.getContent().size() + " top-level messages for channel " + channelId);

        // Batch load reactions for all messages at once
        List<Long> messageIds = messages.stream()
                .map(Message::getId)
                .collect(Collectors.toList());

        Map<Long, Map<String, List<Long>>> reactionsMap = loadReactionsForMessages(messageIds);

        return messages.stream()
                .map(msg -> toMessageResponse(msg, reactionsMap.get(msg.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getThreadReplies(Long userId, Long parentMessageId) {
        Message parentMessage = messageRepository.findById(parentMessageId)
                .orElseThrow(() -> new RuntimeException("Parent message not found"));

        Channel channel = parentMessage.getChannel();

        // 비공개 채널인 경우에만 멤버십 확인
        if (channel.getIsPrivate() && !channelMemberRepository.existsByChannelIdAndUserId(channel.getId(), userId)) {
            throw new RuntimeException("Not a member of this channel");
        }

        List<Message> replies = messageRepository.findByParentMessageIdAndIsDeletedFalseOrderByCreatedAtAsc(parentMessageId);

        // Batch load reactions for all replies at once
        List<Long> messageIds = replies.stream()
                .map(Message::getId)
                .collect(Collectors.toList());

        Map<Long, Map<String, List<Long>>> reactionsMap = loadReactionsForMessages(messageIds);

        return replies.stream()
                .map(msg -> toMessageResponse(msg, reactionsMap.get(msg.getId())))
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateReadState(Long userId, UpdateReadStateRequest request) {
        Channel channel = channelRepository.findById(request.getChannelId())
                .orElseThrow(() -> new RuntimeException("Channel not found"));

        // 비공개 채널인 경우에만 멤버십 확인
        if (channel.getIsPrivate() && !channelMemberRepository.existsByChannelIdAndUserId(request.getChannelId(), userId)) {
            throw new RuntimeException("Not a member of this channel");
        }

        ReadState readState = readStateRepository.findByChannelIdAndUserId(request.getChannelId(), userId)
                .orElse(ReadState.builder()
                        .channelId(request.getChannelId())
                        .userId(userId)
                        .build());

        readState.setLastReadMessageId(request.getLastReadMessageId());
        readStateRepository.save(readState);
    }

    // Batch load reactions for multiple messages at once
    private Map<Long, Map<String, List<Long>>> loadReactionsForMessages(List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return new HashMap<>();
        }

        List<MessageReaction> allReactions = reactionRepository.findAllByMessageIds(messageIds);

        Map<Long, Map<String, List<Long>>> result = new HashMap<>();
        for (MessageReaction reaction : allReactions) {
            result.computeIfAbsent(reaction.getMessageId(), k -> new HashMap<>())
                    .computeIfAbsent(reaction.getEmoji(), k -> new ArrayList<>())
                    .add(reaction.getUserId());
        }

        return result;
    }

    // Convert message to response with pre-loaded reactions
    private MessageResponse toMessageResponse(Message message, Map<String, List<Long>> reactions) {
        User user = userRepository.findById(message.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Mention> mentions = mentionRepository.findByMessageId(message.getId());
        List<MessageResponse.MentionInfo> mentionInfos = mentions.stream()
                .map(m -> MessageResponse.MentionInfo.builder()
                        .userId(m.getMentionedUserId())
                        .mentionType(m.getMentionType())
                        .build())
                .collect(Collectors.toList());

        Integer replyCount = messageRepository.countRepliesByParentMessageId(message.getId());

        return MessageResponse.builder()
                .id(message.getId())
                .workspaceId(message.getWorkspaceId())
                .channelId(message.getChannel().getId())
                .userId(message.getUserId())
                .userName(user.getName())
                .userAvatarUrl(user.getAvatarUrl())
                .content(message.getContent())
                .parentMessageId(message.getParentMessageId())
                .replyCount(replyCount != null ? replyCount : 0)
                .isEdited(message.getIsEdited())
                .mentions(mentionInfos)
                .reactions(reactions != null ? reactions : new HashMap<>())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .editedAt(message.getEditedAt())
                .build();
    }

    // For single message (used by sendMessage and updateMessage)
    private MessageResponse toMessageResponse(Message message) {
        // Load reactions for this single message
        List<MessageReaction> reactions = reactionRepository.findByMessageId(message.getId());
        Map<String, List<Long>> reactionsMap = new HashMap<>();
        for (MessageReaction reaction : reactions) {
            reactionsMap.computeIfAbsent(reaction.getEmoji(), k -> new ArrayList<>())
                    .add(reaction.getUserId());
        }

        return toMessageResponse(message, reactionsMap);
    }

    @Transactional
    public MessageResponse updateMessage(Long userId, Long messageId, String newContent) {
        Message message = messageRepository.findById(messageId)
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
        message.setEditedAt(java.time.LocalDateTime.now());
        message = messageRepository.save(message);

        // RAG API에 청크 업데이트 (chunk_id가 있는 경우에만)
        if (message.getChunkId() != null && !message.getChunkId().isEmpty()) {
            log.info("RAG API 청크 업데이트 시작");
            updateRagChunk(message);
        } else {
            log.warn("chunk_id가 없어서 RAG API 업데이트를 건너뜁니다.");
        }

        MessageResponse messageResponse = toMessageResponse(message);

        // WebSocket으로 메시지 수정 브로드캐스트
        broadcastMessageUpdate(messageResponse);

        return messageResponse;
    }

    @Transactional
    public void deleteMessage(Long userId, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // Check if user owns this message
        if (!message.getUserId().equals(userId)) {
            throw new RuntimeException("Cannot delete message - not the owner");
        }

        Long channelId = message.getChannel().getId();

        message.setIsDeleted(true);
        message.setContent("[삭제된 메시지]");
        messageRepository.save(message);

        // RAG API에서 청크 삭제 (chunk_id가 있는 경우에만)
        if (message.getChunkId() != null && !message.getChunkId().isEmpty()) {
            log.info("RAG API 청크 삭제 시작");
            deleteRagChunk(message.getChunkId());
        } else {
            log.warn("chunk_id가 없어서 RAG API 삭제를 건너뜁니다.");
        }

        // WebSocket으로 메시지 삭제 브로드캐스트
        broadcastMessageDelete(channelId, messageId);
    }

    /**
     * WebSocket으로 메시지 수정 브로드캐스트
     */
    private void broadcastMessageUpdate(MessageResponse message) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("message", message);

            WebSocketMessage wsMessage = WebSocketMessage.builder()
                    .type("MESSAGE_UPDATED")
                    .workspaceId(message.getWorkspaceId())
                    .channelId(message.getChannelId())
                    .payload(payload)
                    .timestamp(LocalDateTime.now())
                    .build();

            // 채널의 모든 사용자에게 브로드캐스트
            messagingTemplate.convertAndSend(
                    "/topic/channel/" + message.getChannelId(),
                    wsMessage
            );

            log.info("메시지 수정 브로드캐스트: channelId={}, messageId={}",
                    message.getChannelId(), message.getId());
        } catch (Exception e) {
            log.error("메시지 수정 브로드캐스트 실패", e);
        }
    }

    /**
     * WebSocket으로 메시지 삭제 브로드캐스트
     */
    private void broadcastMessageDelete(Long channelId, Long messageId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("channelId", channelId);
            payload.put("messageId", messageId);

            WebSocketMessage wsMessage = WebSocketMessage.builder()
                    .type("MESSAGE_DELETED")
                    .channelId(channelId)
                    .payload(payload)
                    .timestamp(LocalDateTime.now())
                    .build();

            // 채널의 모든 사용자에게 브로드캐스트
            messagingTemplate.convertAndSend(
                    "/topic/channel/" + channelId,
                    wsMessage
            );

            log.info("메시지 삭제 브로드캐스트: channelId={}, messageId={}",
                    channelId, messageId);
        } catch (Exception e) {
            log.error("메시지 삭제 브로드캐스트 실패", e);
        }
    }

    /**
     * 메시지의 chunk_id를 업데이트합니다.
     * sendMessage 트랜잭션이 커밋된 후에 실행됩니다.
     *
     * @param event chunk_id 업데이트 이벤트
     */
    @org.springframework.transaction.event.TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void handleChunkIdUpdate(ChunkIdUpdateEvent event) {
        try {
            log.info("chunk_id 업데이트 이벤트 처리 시작: messageId={}, chunkId={}",
                    event.getMessageId(), event.getChunkId());

            Message message = messageRepository.findById(event.getMessageId())
                    .orElseThrow(() -> new RuntimeException("Message not found: " + event.getMessageId()));

            message.setChunkId(event.getChunkId());
            messageRepository.save(message);

            log.info("메시지에 chunk_id 저장 완료: messageId={}, chunkId={}",
                    event.getMessageId(), event.getChunkId());
        } catch (Exception e) {
            log.error("chunk_id 업데이트 실패: messageId={}, chunkId={}, error={}",
                    event.getMessageId(), event.getChunkId(), e.getMessage(), e);
        }
    }

    /**
     * RAG API에 채널 메시지(스레드 및 답변)를 전송합니다.
     *
     * @param message 전송할 메시지
     * @return chunk_id (실패 시 null)
     */
    private String sendToRagApi(Message message) {
        try {
            // thread_id 결정: parentMessageId가 null이면 자기 자신의 ID, 아니면 parentMessageId
            Long threadId = message.getParentMessageId() != null
                    ? message.getParentMessageId()
                    : message.getId();

            IngestChunkRequest ingestRequest = IngestChunkRequest.builder()
                    .chunk(message.getContent())
                    .channelId(message.getChannel().getId())
                    .threadId(threadId)
                    .documentId(null)  // document_id는 null로 설정
                    .userId(message.getUserId())
                    .createdDate(message.getCreatedAt())
                    .updatedDate(message.getUpdatedAt())
                    .build();

            log.info("채널 메시지를 RAG API로 전송: messageId={}, channelId={}, threadId={}",
                    message.getId(), message.getChannel().getId(), threadId);

            // RAG API로 전송하고 chunk_id 반환
            String chunkId = ragApiClient.ingestChunk(ingestRequest);
            if (chunkId != null) {
                log.info("RAG API 전송 성공: messageId={}, chunkId={}", message.getId(), chunkId);
            }
            return chunkId;

        } catch (Exception e) {
            log.error("RAG API 전송 중 오류 발생: messageId={}, error={}",
                    message.getId(), e.getMessage(), e);
            // 에러가 발생해도 메시지 저장은 계속 진행
            return null;
        }
    }

    /**
     * RAG API의 청크를 업데이트합니다.
     *
     * @param message 업데이트할 메시지
     */
    private void updateRagChunk(Message message) {
        try {
            // thread_id 결정: parentMessageId가 null이면 자기 자신의 ID, 아니면 parentMessageId
            Long threadId = message.getParentMessageId() != null
                    ? message.getParentMessageId()
                    : message.getId();

            UpdateChunkRequest updateRequest = UpdateChunkRequest.builder()
                    .chunk(message.getContent())
                    .channelId(message.getChannel().getId())
                    .threadId(threadId)
                    .documentId(null)
                    .build();

            log.info("RAG API 청크 업데이트 요청: messageId={}, chunkId={}",
                    message.getId(), message.getChunkId());

            ChunkResponse response = ragApiClient.updateChunk(
                    message.getChunkId(),
                    updateRequest,
                    "message_platform"
            );

            if ("success".equals(response.getStatus())) {
                log.info("RAG API 청크 업데이트 성공: chunkId={}", message.getChunkId());
            } else {
                log.warn("RAG API 청크 업데이트 실패: chunkId={}, message={}",
                        message.getChunkId(), response.getMessage());
            }

        } catch (Exception e) {
            log.error("RAG API 청크 업데이트 중 오류 발생: messageId={}, chunkId={}, error={}",
                    message.getId(), message.getChunkId(), e.getMessage(), e);
            // 에러가 발생해도 메시지 업데이트는 계속 진행
        }
    }

    /**
     * RAG API의 청크를 삭제합니다.
     *
     * @param chunkId 삭제할 청크 ID
     */
    private void deleteRagChunk(String chunkId) {
        try {
            log.info("RAG API 청크 삭제 요청: chunkId={}", chunkId);

            ChunkResponse response = ragApiClient.deleteChunk(chunkId, "message_platform");

            if ("success".equals(response.getStatus())) {
                log.info("RAG API 청크 삭제 성공: chunkId={}", chunkId);
            } else {
                log.warn("RAG API 청크 삭제 실패: chunkId={}, message={}",
                        chunkId, response.getMessage());
            }

        } catch (Exception e) {
            log.error("RAG API 청크 삭제 중 오류 발생: chunkId={}, error={}",
                    chunkId, e.getMessage(), e);
            // 에러가 발생해도 메시지 삭제는 계속 진행
        }
    }
}
