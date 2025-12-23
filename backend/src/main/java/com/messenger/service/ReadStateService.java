package com.messenger.service;

import com.messenger.entity.ReadState;
import com.messenger.repository.ReadStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 읽음 상태 관리 서비스
 * - last_read_message_id는 NULL 허용
 * - 메시지 삭제 시 자동 NULL 처리 (ON DELETE SET NULL)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadStateService {

    private final ReadStateRepository readStateRepository;

    /**
     * 읽음 상태 업데이트 (중복 방지)
     *
     * @param userId 사용자 ID
     * @param channelId 채널 ID
     * @param lastReadMessageId 마지막 읽은 메시지 ID (NULL 가능)
     */
    @Transactional
    public void updateReadState(Long userId, Long channelId, Long lastReadMessageId) {
        Optional<ReadState> existing = readStateRepository.findByChannelIdAndUserId(channelId, userId);

        if (existing.isPresent()) {
            // 기존 ReadState 업데이트
            ReadState readState = existing.get();
            readState.setLastReadMessageId(lastReadMessageId);
            readStateRepository.save(readState);
            log.debug("Updated read state: userId={}, channelId={}, messageId={}",
                    userId, channelId, lastReadMessageId);
        } else {
            // 새 ReadState 생성
            ReadState readState = ReadState.builder()
                    .channelId(channelId)
                    .userId(userId)
                    .lastReadMessageId(lastReadMessageId)
                    .build();

            try {
                readStateRepository.save(readState);
                log.debug("Created read state: userId={}, channelId={}, messageId={}",
                        userId, channelId, lastReadMessageId);
            } catch (DataIntegrityViolationException e) {
                // UNIQUE 제약 위반 - 동시 요청으로 이미 생성됨
                // 재시도: 조회 후 업데이트
                log.warn("Duplicate read_state detected, retrying update");
                ReadState retry = readStateRepository.findByChannelIdAndUserId(channelId, userId)
                        .orElseThrow(() -> new RuntimeException("Read state not found after retry"));
                retry.setLastReadMessageId(lastReadMessageId);
                readStateRepository.save(retry);
            }
        }
    }

    /**
     * 읽음 상태 조회
     *
     * @return last_read_message_id (NULL 가능)
     */
    @Transactional(readOnly = true)
    public Long getLastReadMessageId(Long userId, Long channelId) {
        return readStateRepository.findByChannelIdAndUserId(channelId, userId)
                .map(ReadState::getLastReadMessageId)
                .orElse(null); // ReadState가 없으면 NULL
    }

    /**
     * 안 읽은 메시지 개수 계산
     *
     * @param userId 사용자 ID
     * @param channelId 채널 ID
     * @param latestMessageId 채널의 최신 메시지 ID
     * @return 안 읽은 메시지 개수
     */
    public int calculateUnreadCount(Long userId, Long channelId, Long latestMessageId) {
        Long lastReadMessageId = getLastReadMessageId(userId, channelId);

        if (lastReadMessageId == null) {
            // 한 번도 읽지 않음 - 모든 메시지가 안 읽음
            return 0; // 또는 적절한 계산
        }

        if (latestMessageId == null || latestMessageId <= lastReadMessageId) {
            return 0; // 모두 읽음
        }

        return (int) (latestMessageId - lastReadMessageId);
    }
}
