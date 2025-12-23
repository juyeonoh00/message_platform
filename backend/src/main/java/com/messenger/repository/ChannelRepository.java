package com.messenger.repository;

import com.messenger.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {

    List<Channel> findByWorkspaceId(Long workspaceId);

    List<Channel> findByWorkspaceIdAndIsPrivate(Long workspaceId, Boolean isPrivate);

    Optional<Channel> findByIdAndWorkspaceId(Long id, Long workspaceId);

    @Query("SELECT c FROM Channel c WHERE c.workspace.id = :workspaceId AND c.isPrivate = false")
    List<Channel> findPublicChannelsByWorkspaceId(Long workspaceId);
}
