package com.messenger.repository;

import com.messenger.entity.ChatroomMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatroomMessageRepository extends JpaRepository<ChatroomMessage, Long> {

    Page<ChatroomMessage> findByChatroomIdAndIsDeletedFalseOrderByCreatedAtAsc(Long chatroomId, Pageable pageable);

    List<ChatroomMessage> findByChatroomIdAndIsDeletedFalseOrderByCreatedAtAsc(Long chatroomId);

    List<ChatroomMessage> findByChatroomId(Long chatroomId);

    void deleteByChatroomId(Long chatroomId);
}
