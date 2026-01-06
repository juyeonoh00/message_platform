package com.messenger.controller;

import com.messenger.config.security.JwtTokenProvider;
import com.messenger.dto.notification.NotificationResponse;
import com.messenger.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 워크스페이스별 알림 조회
     */
    @GetMapping("/workspace/{workspaceId}")
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @PathVariable Long workspaceId,
            @RequestHeader("Authorization") String token) {

        Long userId = jwtTokenProvider.getUserIdFromToken(token.replace("Bearer ", ""));
        List<NotificationResponse> notifications = notificationService.getNotificationsByWorkspace(userId, workspaceId);
        return ResponseEntity.ok(notifications);
    }

    /**
     * 읽지 않은 알림 조회
     */
    @GetMapping("/workspace/{workspaceId}/unread")
    public ResponseEntity<List<NotificationResponse>> getUnreadNotifications(
            @PathVariable Long workspaceId,
            @RequestHeader("Authorization") String token) {

        Long userId = jwtTokenProvider.getUserIdFromToken(token.replace("Bearer ", ""));
        List<NotificationResponse> notifications = notificationService.getUnreadNotifications(userId, workspaceId);
        return ResponseEntity.ok(notifications);
    }

    /**
     * 읽지 않은 알림 개수 조회
     */
    @GetMapping("/workspace/{workspaceId}/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @PathVariable Long workspaceId,
            @RequestHeader("Authorization") String token) {

        Long userId = jwtTokenProvider.getUserIdFromToken(token.replace("Bearer ", ""));
        Long count = notificationService.getUnreadCount(userId, workspaceId);

        Map<String, Long> response = new HashMap<>();
        response.put("count", count);
        return ResponseEntity.ok(response);
    }

    /**
     * 알림을 읽음으로 표시
     */
    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Map<String, String>> markAsRead(
            @PathVariable Long notificationId,
            @RequestHeader("Authorization") String token) {

        Long userId = jwtTokenProvider.getUserIdFromToken(token.replace("Bearer ", ""));
        notificationService.markAsRead(notificationId, userId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Notification marked as read");
        return ResponseEntity.ok(response);
    }

    /**
     * 워크스페이스의 모든 알림을 읽음으로 표시
     */
    @PutMapping("/workspace/{workspaceId}/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(
            @PathVariable Long workspaceId,
            @RequestHeader("Authorization") String token) {

        Long userId = jwtTokenProvider.getUserIdFromToken(token.replace("Bearer ", ""));
        int count = notificationService.markAllAsRead(userId, workspaceId);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "All notifications marked as read");
        response.put("count", count);
        return ResponseEntity.ok(response);
    }
}
