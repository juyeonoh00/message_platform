package com.messenger.service;

import com.messenger.entity.Channel;
import com.messenger.entity.ChannelMember;
import com.messenger.entity.User;
import com.messenger.repository.ChannelMemberRepository;
import com.messenger.repository.ChannelRepository;
import com.messenger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채널 멤버십 관리 서비스
 * - public 채널: 자동 가입 (명시적 row 없어도 접근 가능)
 * - private 채널: 명시적 row 필요
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelMembershipService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final UserRepository userRepository;

    /**
     * 채널 가입 (UNIQUE 제약 중복 방지)
     */
    @Transactional
    public void joinChannel(Long userId, Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));

        // public 채널은 자동 가입되므로 row 생성 선택적
        if (!channel.getIsPrivate()) {
            log.debug("Public channel - implicit membership for userId={}", userId);
            // 선택: row를 생성하지 않아도 접근 가능하도록 로직 구성
            // 여기서는 명시적으로 row 생성
        }

        // 이미 가입되어 있는지 확인 (UNIQUE 제약 위반 방지)
        if (channelMemberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            log.warn("User {} already member of channel {}", userId, channelId);
            return; // 이미 가입됨 - 조용히 반환
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ChannelMember member = ChannelMember.builder()
                .channel(channel)
                .user(user)
                .role("MEMBER")
                .build();

        try {
            channelMemberRepository.save(member);
            log.info("User {} joined channel {}", userId, channelId);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE 제약 위반 - 이미 가입됨 (동시 요청 등)
            log.warn("Duplicate membership detected for userId={}, channelId={}", userId, channelId);
            // 조용히 무시하거나 적절한 예외 처리
        }
    }

    /**
     * 채널 멤버인지 확인
     * - public 채널: Workspace 멤버면 자동 허용
     * - private 채널: 명시적 row 필요
     */
    @Transactional(readOnly = true)
    public boolean isMember(Long userId, Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));

        // public 채널은 Workspace 멤버면 자동 허용
        if (!channel.getIsPrivate()) {
            return true; // 임시: 모든 public 채널 접근 허용
        }

        // private 채널은 명시적 멤버십 필요
        return channelMemberRepository.existsByChannelIdAndUserId(channelId, userId);
    }

    /**
     * 채널 탈퇴
     */
    @Transactional
    public void leaveChannel(Long userId, Long channelId) {
        ChannelMember member = channelMemberRepository.findByChannelIdAndUserId(channelId, userId)
                .orElseThrow(() -> new RuntimeException("Not a member of this channel"));

        channelMemberRepository.delete(member);
        log.info("User {} left channel {}", userId, channelId);
    }
}
