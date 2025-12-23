package com.messenger.service;

import com.messenger.entity.Mention;
import com.messenger.entity.Message;
import com.messenger.repository.MentionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 멘션 관리 서비스
 * - @user: mentioned_user_id 필수
 * - @channel/@here/@everyone: mentioned_user_id NULL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MentionService {

    private final MentionRepository mentionRepository;

    /**
     * 멘션 생성 (중복 방지)
     *
     * @param message 메시지
     * @param mentionedUserId 멘션 대상 (NULL 가능)
     * @param mentionType user|channel|here|everyone
     */
    @Transactional
    public void createMention(Message message, Long mentionedUserId, String mentionType) {
        // 검증: @user는 mentioned_user_id 필수
        if ("user".equals(mentionType) && mentionedUserId == null) {
            throw new IllegalArgumentException("mentioned_user_id is required for @user mention");
        }

        // 검증: @channel/@here/@everyone은 mentioned_user_id NULL
        if (("channel".equals(mentionType) || "here".equals(mentionType) || "everyone".equals(mentionType))
                && mentionedUserId != null) {
            log.warn("mentioned_user_id should be NULL for {}", mentionType);
            mentionedUserId = null; // 강제 NULL
        }

        Mention mention = Mention.builder()
                .message(message)
                .mentionedUserId(mentionedUserId)
                .mentionType(mentionType)
                .isRead(false)
                .build();

        try {
            mentionRepository.save(mention);
            log.debug("Created mention: type={}, userId={}", mentionType, mentionedUserId);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE 제약 위반 - 동일 멘션 중복
            log.warn("Duplicate mention detected: messageId={}, userId={}, type={}",
                    message.getId(), mentionedUserId, mentionType);
        }
    }

    /**
     * 특정 유저의 읽지 않은 멘션 조회
     */
    @Transactional(readOnly = true)
    public List<Mention> getUnreadMentions(Long userId) {
        return mentionRepository.findByMentionedUserIdAndIsReadFalse(userId);
    }

    /**
     * 멘션 읽음 처리
     */
    @Transactional
    public void markAsRead(Long mentionId) {
        Mention mention = mentionRepository.findById(mentionId)
                .orElseThrow(() -> new RuntimeException("Mention not found"));

        mention.setIsRead(true);
        mentionRepository.save(mention);
    }

    /**
     * 유저의 모든 멘션 읽음 처리
     */
    @Transactional
    public void markAllAsRead(Long userId) {
        List<Mention> mentions = mentionRepository.findByMentionedUserIdAndIsReadFalse(userId);
        mentions.forEach(m -> m.setIsRead(true));
        mentionRepository.saveAll(mentions);
        log.info("Marked {} mentions as read for userId={}", mentions.size(), userId);
    }
}
