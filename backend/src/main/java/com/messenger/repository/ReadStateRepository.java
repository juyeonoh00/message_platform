package com.messenger.repository;

import com.messenger.entity.ReadState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReadStateRepository extends JpaRepository<ReadState, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rs FROM ReadState rs WHERE rs.channel.id = :channelId AND rs.user.id = :userId")
    Optional<ReadState> findByChannelIdAndUserIdWithLock(@Param("channelId") Long channelId, @Param("userId") Long userId);

    Optional<ReadState> findByChannelIdAndUserId(Long channelId, Long userId);

    List<ReadState> findByUserId(Long userId);

    List<ReadState> findByChannelId(Long channelId);
}
