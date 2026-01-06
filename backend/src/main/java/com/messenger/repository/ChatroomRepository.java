package com.messenger.repository;

import com.messenger.entity.Chatroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatroomRepository extends JpaRepository<Chatroom, Long> {

    @Query("SELECT c FROM Chatroom c WHERE c.id IN (SELECT cm.chatroom.id FROM ChatroomMember cm WHERE cm.user.id = :userId)")
    List<Chatroom> findByUserId(Long userId);

    @Query("SELECT c FROM Chatroom c WHERE c.id IN " +
           "(SELECT cm1.chatroom.id FROM ChatroomMember cm1 WHERE cm1.user.id = :userId1) " +
           "AND c.id IN (SELECT cm2.chatroom.id FROM ChatroomMember cm2 WHERE cm2.user.id = :userId2) " +
           "AND (SELECT COUNT(cm3) FROM ChatroomMember cm3 WHERE cm3.chatroom.id = c.id) = 2")
    Optional<Chatroom> findDirectChatroomBetweenUsers(Long userId1, Long userId2);
}
