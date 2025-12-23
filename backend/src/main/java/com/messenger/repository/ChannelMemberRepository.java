package com.messenger.repository;

import com.messenger.entity.ChannelMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {

    List<ChannelMember> findByChannelId(Long channelId);

    List<ChannelMember> findByUserId(Long userId);

    Optional<ChannelMember> findByChannelIdAndUserId(Long channelId, Long userId);

    boolean existsByChannelIdAndUserId(Long channelId, Long userId);

    @Query("SELECT cm FROM ChannelMember cm WHERE cm.channel.workspace.id = :workspaceId AND cm.user.id = :userId")
    List<ChannelMember> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);
}
