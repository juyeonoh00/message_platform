package com.messenger.service;

import com.messenger.dto.notification.CreateMentionNotificationRequest;
import com.messenger.dto.notification.NotificationResponse;
import com.messenger.entity.Notification;
import com.messenger.entity.User;
import com.messenger.repository.NotificationRepository;
import com.messenger.repository.UserRepository;
import com.messenger.websocket.WebSocketMessagePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final WebSocketMessagePublisher webSocketMessagePublisher;
    private final UserRepository userRepository;

    @Autowired(required = false)
    private FcmService fcmService;

    public NotificationService(
            NotificationRepository notificationRepository,
            WebSocketMessagePublisher webSocketMessagePublisher,
            UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.webSocketMessagePublisher = webSocketMessagePublisher;
        this.userRepository = userRepository;
    }

    /**
     * 멘션 알림 생성
     */
    @Transactional
    public Notification createMentionNotification(CreateMentionNotificationRequest request) {
        String notificationContent;

        if (request.getChannelId() != null) {
            // 채널 멘션
            if ("CHANNEL".equals(request.getMentionType()) || "EVERYONE".equals(request.getMentionType())) {
                notificationContent = String.format("%s mentioned @%s: %s",
                        request.getSenderName(), request.getMentionType().toLowerCase(), request.getMessageContent());
            } else if ("HERE".equals(request.getMentionType())) {
                notificationContent = String.format("%s mentioned @here: %s",
                        request.getSenderName(), request.getMessageContent());
            } else {
                notificationContent = String.format("%s mentioned you: %s",
                        request.getSenderName(), request.getMessageContent());
            }
        } else if (request.getChatroomId() != null) {
            // 채팅방 멘션
            if ("EVERYONE".equals(request.getMentionType())) {
                notificationContent = String.format("%s mentioned @everyone: %s",
                        request.getSenderName(), request.getMessageContent());
            } else if ("HERE".equals(request.getMentionType())) {
                notificationContent = String.format("%s mentioned @here: %s",
                        request.getSenderName(), request.getMessageContent());
            } else {
                notificationContent = String.format("%s mentioned you: %s",
                        request.getSenderName(), request.getMessageContent());
            }
        } else {
            notificationContent = String.format("%s mentioned you: %s",
                    request.getSenderName(), request.getMessageContent());
        }

        Notification notification = Notification.builder()
                .userId(request.getUserId())
                .workspaceId(request.getWorkspaceId())
                .type("MENTION")
                .content(notificationContent)
                .channelId(request.getChannelId())
                .chatroomId(request.getChatroomId())
                .messageId(request.getMessageId())
                .senderId(request.getSenderId())
                .senderName(request.getSenderName())
                .senderAvatarUrl(request.getSenderAvatarUrl())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Created mention notification for user {} in workspace {}", request.getUserId(), request.getWorkspaceId());

        // Send notification via WebSocket
        NotificationResponse notificationResponse = toResponse(saved);
        webSocketMessagePublisher.publishNotification(request.getWorkspaceId(), request.getUserId(), notificationResponse);

        // Send FCM push notification
        sendFcmNotification(request, notificationContent);

        return saved;
    }

    /**
     * 워크스페이스별 알림 조회 (최신 20개)
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsByWorkspace(Long userId, Long workspaceId) {
        List<Notification> notifications = notificationRepository.findByUserIdAndWorkspaceIdOrderByCreatedAtDesc(
                userId, workspaceId, PageRequest.of(0, 20));
        return notifications.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 읽지 않은 알림 조회
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications(Long userId, Long workspaceId) {
        List<Notification> notifications = notificationRepository.findByUserIdAndWorkspaceIdAndIsReadFalseOrderByCreatedAtDesc(
                userId, workspaceId);
        return notifications.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 읽지 않은 알림 개수 (최신 20개 알림 중에서만 카운트)
     */
    @Transactional(readOnly = true)
    public Long getUnreadCount(Long userId, Long workspaceId) {
        List<Notification> recentNotifications = notificationRepository.findByUserIdAndWorkspaceIdOrderByCreatedAtDesc(
                userId, workspaceId, PageRequest.of(0, 20));
        return recentNotifications.stream()
                .filter(n -> !n.getIsRead())
                .count();
    }

    /**
     * 알림을 읽음으로 표시
     */
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.getUserId().equals(userId)) {
            throw new RuntimeException("Not authorized to mark this notification as read");
        }

        if (!notification.getIsRead()) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
            log.info("Marked notification {} as read", notificationId);
        }
    }

    /**
     * 워크스페이스의 모든 알림을 읽음으로 표시
     */
    @Transactional
    public int markAllAsRead(Long userId, Long workspaceId) {
        int count = notificationRepository.markAllAsRead(userId, workspaceId);
        log.info("Marked {} notifications as read for user {} in workspace {}", count, userId, workspaceId);
        return count;
    }

    /**
     * FCM 푸시 알림 전송
     * Firebase 설정이 없어도 앱은 정상 동작합니다 (WebSocket 알림은 계속 작동)
     */
    private void sendFcmNotification(CreateMentionNotificationRequest request, String content) {
        // FCM Service가 활성화되지 않은 경우 스킵
        if (fcmService == null) {
            log.debug("FCM service is not available (fcm.enabled=false). Skipping push notification.");
            return;
        }

        try {
            User user = userRepository.findById(request.getUserId())
                    .orElse(null);

            if (user == null) {
                log.debug("User not found for FCM notification: userId={}", request.getUserId());
                return;
            }

            // FCM 데이터 구성
            Map<String, String> data = new HashMap<>();
            data.put("type", "MENTION");
            data.put("workspaceId", String.valueOf(request.getWorkspaceId()));

            if (request.getChannelId() != null) {
                data.put("channelId", String.valueOf(request.getChannelId()));
            }
            if (request.getChatroomId() != null) {
                data.put("chatroomId", String.valueOf(request.getChatroomId()));
            }
            if (request.getMessageId() != null) {
                data.put("messageId", String.valueOf(request.getMessageId()));
            }

            // FCM 알림 전송 (우선순위 로직 포함: DESKTOP_APP > WEB)
            fcmService.sendNotificationToUser(user, request.getSenderName(), content, data);

        } catch (Exception e) {
            // FCM 실패해도 앱은 계속 동작 (WebSocket 알림은 정상 작동)
            log.debug("FCM notification failed: {}", e.getMessage());
        }
    }

    /**
     * Notification 엔티티를 NotificationResponse DTO로 변환
     */
    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .workspaceId(notification.getWorkspaceId())
                .type(notification.getType())
                .content(notification.getContent())
                .channelId(notification.getChannelId())
                .chatroomId(notification.getChatroomId())
                .messageId(notification.getMessageId())
                .senderId(notification.getSenderId())
                .senderName(notification.getSenderName())
                .senderAvatarUrl(notification.getSenderAvatarUrl())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .build();
    }
}
