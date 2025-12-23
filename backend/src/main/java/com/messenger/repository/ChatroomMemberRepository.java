package com.messenger.repository;

import com.messenger.entity.ChatroomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatroomMemberRepository extends JpaRepository<ChatroomMember, Long> {

    List<ChatroomMember> findByChatroomId(Long chatroomId);

    List<ChatroomMember> findByUserId(Long userId);

    Optional<ChatroomMember> findByChatroomIdAndUserId(Long chatroomId, Long userId);

    boolean existsByChatroomIdAndUserId(Long chatroomId, Long userId);

    void deleteByChatroomId(Long chatroomId);
}
