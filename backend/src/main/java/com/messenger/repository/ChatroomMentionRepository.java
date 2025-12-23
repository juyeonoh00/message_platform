package com.messenger.repository;

import com.messenger.entity.ChatroomMention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatroomMentionRepository extends JpaRepository<ChatroomMention, Long> {

    List<ChatroomMention> findByChatroomMessageId(Long chatroomMessageId);
}
