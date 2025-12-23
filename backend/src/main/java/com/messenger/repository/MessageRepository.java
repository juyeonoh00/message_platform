package com.messenger.repository;

import com.messenger.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByChannelIdAndIsDeletedFalseOrderByCreatedAtAsc(Long channelId, Pageable pageable);

    // Get top-level messages only (exclude thread replies)
    Page<Message> findByChannelIdAndParentMessageIdIsNullAndIsDeletedFalseOrderByCreatedAtAsc(Long channelId, Pageable pageable);

    List<Message> findByChannelIdAndIsDeletedFalseOrderByCreatedAtAsc(Long channelId);

    List<Message> findByChannelId(Long channelId);

    // Get top-level messages only (exclude thread replies)
    List<Message> findByChannelIdAndParentMessageIdIsNullAndIsDeletedFalseOrderByCreatedAtAsc(Long channelId);

    List<Message> findByParentMessageIdAndIsDeletedFalseOrderByCreatedAtAsc(Long parentMessageId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.parentMessageId = :messageId AND m.isDeleted = false")
    Integer countRepliesByParentMessageId(Long messageId);

    Optional<Message> findByIdAndChannelIdAndIsDeletedFalse(Long id, Long channelId);

    @Query("SELECT m FROM Message m WHERE m.channel.id = :channelId AND m.createdAt > :since AND m.isDeleted = false ORDER BY m.createdAt DESC")
    List<Message> findRecentMessages(Long channelId, LocalDateTime since);

    @Query("SELECT m FROM Message m WHERE m.workspaceId = :workspaceId AND m.channel.id = :channelId AND m.createdAt >= :startTime AND m.createdAt <= :endTime AND m.isDeleted = false")
    List<Message> findByWorkspaceAndChannelAndTimeRange(Long workspaceId, Long channelId, LocalDateTime startTime, LocalDateTime endTime);

    // For reindexing all messages
    List<Message> findByIsDeletedFalse();

    // For reindexing workspace messages
    List<Message> findByWorkspaceIdAndIsDeletedFalse(Long workspaceId);
}
