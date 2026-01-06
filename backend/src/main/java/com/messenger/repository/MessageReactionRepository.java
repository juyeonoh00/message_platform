package com.messenger.repository;

import com.messenger.entity.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    List<MessageReaction> findByMessage_Id(Long messageId);

    List<MessageReaction> findByMessage_IdIn(List<Long> messageIds);

    Optional<MessageReaction> findByMessage_IdAndUser_IdAndEmoji(Long messageId, Long userId, String emoji);

    void deleteByMessage_IdAndUser_IdAndEmoji(Long messageId, Long userId, String emoji);

    @Query("SELECT mr FROM MessageReaction mr WHERE mr.message.id IN :messageIds")
    List<MessageReaction> findAllByMessageIds(@Param("messageIds") List<Long> messageIds);
}
