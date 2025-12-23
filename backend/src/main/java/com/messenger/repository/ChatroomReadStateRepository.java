package com.messenger.repository;

import com.messenger.entity.ChatroomReadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatroomReadStateRepository extends JpaRepository<ChatroomReadState, Long> {

    Optional<ChatroomReadState> findByChatroomIdAndUserId(Long chatroomId, Long userId);
}
