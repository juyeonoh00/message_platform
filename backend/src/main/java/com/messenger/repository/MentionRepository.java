package com.messenger.repository;

import com.messenger.entity.Mention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MentionRepository extends JpaRepository<Mention, Long> {

    List<Mention> findByMessageId(Long messageId);

    List<Mention> findByMentionedUserIdAndIsReadFalse(Long userId);

    @Query("SELECT m FROM Mention m WHERE m.mentionedUserId = :userId AND m.isRead = false ORDER BY m.message.createdAt DESC")
    List<Mention> findUnreadMentionsByUserId(Long userId);

    @Query("SELECT COUNT(m) FROM Mention m WHERE m.mentionedUserId = :userId AND m.isRead = false")
    Long countUnreadMentionsByUserId(Long userId);

    @Query("SELECT m FROM Mention m WHERE m.message.channel.id = :channelId")
    List<Mention> findByChannelId(Long channelId);
}
