package com.messenger.repository;

import com.messenger.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 워크스페이스별 알림 조회 (최신순, 페이징)
    List<Notification> findByUserIdAndWorkspaceIdOrderByCreatedAtDesc(
            Long userId, Long workspaceId, Pageable pageable);

    // 읽지 않은 알림 조회
    List<Notification> findByUserIdAndWorkspaceIdAndIsReadFalseOrderByCreatedAtDesc(
            Long userId, Long workspaceId);

    // 읽지 않은 알림 개수
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.workspaceId = :workspaceId AND n.isRead = false")
    Long countUnreadNotifications(@Param("userId") Long userId, @Param("workspaceId") Long workspaceId);

    // 모든 알림을 읽음으로 표시
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.workspaceId = :workspaceId AND n.isRead = false")
    int markAllAsRead(@Param("userId") Long userId, @Param("workspaceId") Long workspaceId);

    // 특정 메시지와 관련된 알림 조회
    List<Notification> findByMessageId(Long messageId);

    // 특정 채널의 알림 조회
    List<Notification> findByChannelId(Long channelId);

    // 특정 채팅방의 알림 조회
    List<Notification> findByChatroomId(Long chatroomId);
}
