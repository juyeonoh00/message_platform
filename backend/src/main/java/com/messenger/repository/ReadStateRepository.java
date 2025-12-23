package com.messenger.repository;

import com.messenger.entity.ReadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReadStateRepository extends JpaRepository<ReadState, Long> {

    Optional<ReadState> findByChannelIdAndUserId(Long channelId, Long userId);

    List<ReadState> findByUserId(Long userId);

    List<ReadState> findByChannelId(Long channelId);
}
