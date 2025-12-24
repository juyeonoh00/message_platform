package com.messenger.service;

import com.messenger.dto.channel.*;
import com.messenger.dto.user.UserResponse;
import com.messenger.entity.*;
import com.messenger.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final ReadStateRepository readStateRepository;
    private final MentionRepository mentionRepository;
    private final ElasticsearchService elasticsearchService;

    @Transactional
    public ChannelResponse createChannel(Long userId, CreateChannelRequest request) {
        // Verify user is workspace member
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(request.getWorkspaceId(), userId)) {
            throw new RuntimeException("Not a member of this workspace");
        }

        Workspace workspace = workspaceRepository.findById(request.getWorkspaceId())
                .orElseThrow(() -> new RuntimeException("Workspace not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Channel channel = Channel.builder()
                .workspace(workspace)
                .name(request.getName())
                .description(request.getDescription())
                .isPrivate(request.getIsPrivate())
                .createdBy(userId)
                .build();

        channel = channelRepository.save(channel);

        // Add creator as member with ADMIN role
        ChannelMember member = ChannelMember.builder()
                .channel(channel)
                .user(user)
                .role("ADMIN")
                .build();

        channelMemberRepository.save(member);

        return ChannelResponse.builder()
                .id(channel.getId())
                .workspaceId(channel.getWorkspace().getId())
                .name(channel.getName())
                .description(channel.getDescription())
                .isPrivate(channel.getIsPrivate())
                .createdBy(channel.getCreatedBy())
                .isMember(true)
                .unreadCount(0)
                .createdAt(channel.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ChannelResponse> getWorkspaceChannels(Long userId, Long workspaceId) {
        // Verify user is workspace member
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new RuntimeException("Not a member of this workspace");
        }

        // Get all channels in workspace (no DMs anymore)
        List<Channel> channels = channelRepository.findByWorkspaceId(workspaceId);
        List<ChannelMember> userChannelMemberships = channelMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId);

        return channels.stream()
                .filter(channel -> !channel.getIsPrivate() || userChannelMemberships.stream()
                        .anyMatch(cm -> cm.getChannel().getId().equals(channel.getId())))
                .map(channel -> {
                    // 공개 채널은 모든 워크스페이스 멤버가 자동으로 멤버
                    // 비공개 채널은 명시적으로 초대된 경우에만 멤버
                    boolean isMember = !channel.getIsPrivate() || userChannelMemberships.stream()
                            .anyMatch(cm -> cm.getChannel().getId().equals(channel.getId()));

                    int unreadCount = calculateUnreadCount(userId, channel.getId());

                    return ChannelResponse.builder()
                            .id(channel.getId())
                            .workspaceId(channel.getWorkspace().getId())
                            .name(channel.getName())
                            .description(channel.getDescription())
                            .isPrivate(channel.getIsPrivate())
                            .createdBy(channel.getCreatedBy())
                            .isMember(isMember)
                            .unreadCount(unreadCount)
                            .createdAt(channel.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void joinChannel(Long userId, Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));

        if (channel.getIsPrivate()) {
            throw new RuntimeException("Cannot join private channel");
        }

        // Verify user is workspace member
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(channel.getWorkspace().getId(), userId)) {
            throw new RuntimeException("Not a member of this workspace");
        }

        if (channelMemberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            throw new RuntimeException("Already a member of this channel");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ChannelMember member = ChannelMember.builder()
                .channel(channel)
                .user(user)
                .role("MEMBER")
                .build();

        channelMemberRepository.save(member);
    }

    @Transactional
    public void leaveChannel(Long userId, Long channelId) {
        ChannelMember member = channelMemberRepository.findByChannelIdAndUserId(channelId, userId)
                .orElseThrow(() -> new RuntimeException("Not a member of this channel"));

        channelMemberRepository.delete(member);
    }

    @Transactional
    public void addMemberToChannel(Long requestUserId, Long channelId, Long targetUserId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));

        // Verify requesting user is a member of the channel
        if (!channelMemberRepository.existsByChannelIdAndUserId(channelId, requestUserId)) {
            throw new RuntimeException("You are not a member of this channel");
        }

        // Verify target user is a workspace member
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(channel.getWorkspace().getId(), targetUserId)) {
            throw new RuntimeException("Target user is not a member of this workspace");
        }

        // Check if target user is already a member
        if (channelMemberRepository.existsByChannelIdAndUserId(channelId, targetUserId)) {
            throw new RuntimeException("User is already a member of this channel");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ChannelMember member = ChannelMember.builder()
                .channel(channel)
                .user(targetUser)
                .role("MEMBER")
                .build();

        channelMemberRepository.save(member);
    }

    @Transactional
    public void deleteChannel(Long userId, Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));

        // Only creator can delete the channel
        if (!channel.getCreatedBy().equals(userId)) {
            throw new RuntimeException("Only channel creator can delete the channel");
        }

        // Delete all mentions associated with messages in this channel
        List<Mention> mentions = mentionRepository.findByChannelId(channelId);
        mentionRepository.deleteAll(mentions);

        // Delete all channel members
        List<ChannelMember> members = channelMemberRepository.findByChannelId(channelId);
        channelMemberRepository.deleteAll(members);

        // Delete all read states
        List<ReadState> readStates = readStateRepository.findByChannelId(channelId);
        readStateRepository.deleteAll(readStates);

        // Delete all messages from Elasticsearch
        elasticsearchService.deleteMessagesByChannelId(channelId);

        // Soft delete all messages in the channel
        List<Message> messages = messageRepository.findByChannelId(channelId);
        messages.forEach(msg -> {
            msg.setIsDeleted(true);
            msg.setContent("[삭제된 메시지]");
        });
        messageRepository.saveAll(messages);

        // Delete the channel
        channelRepository.delete(channel);
    }

    @Transactional
    public ChannelResponse updateChannelName(Long userId, Long channelId, String newName) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));

        // Verify user is channel creator or member
        if (!channel.getCreatedBy().equals(userId) &&
            !channelMemberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            throw new RuntimeException("Only channel creator or members can update the channel");
        }

        channel.setName(newName);
        channel = channelRepository.save(channel);

        boolean isMember = channelMemberRepository.existsByChannelIdAndUserId(channelId, userId);
        int unreadCount = calculateUnreadCount(userId, channelId);

        return ChannelResponse.builder()
                .id(channel.getId())
                .workspaceId(channel.getWorkspace().getId())
                .name(channel.getName())
                .description(channel.getDescription())
                .isPrivate(channel.getIsPrivate())
                .createdBy(channel.getCreatedBy())
                .isMember(isMember)
                .unreadCount(unreadCount)
                .createdAt(channel.getCreatedAt())
                .build();
    }

    @Transactional
    public void removeMemberFromChannel(Long requestUserId, Long channelId, Long targetUserId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));

        // Only creator can remove members
        if (!channel.getCreatedBy().equals(requestUserId)) {
            throw new RuntimeException("Only channel creator can remove members");
        }

        // Cannot remove creator
        if (channel.getCreatedBy().equals(targetUserId)) {
            throw new RuntimeException("Cannot remove channel creator");
        }

        ChannelMember member = channelMemberRepository.findByChannelIdAndUserId(channelId, targetUserId)
                .orElseThrow(() -> new RuntimeException("User is not a member of this channel"));

        channelMemberRepository.delete(member);
    }

    @Transactional
    public ChannelResponse updateChannelPrivacy(Long userId, Long channelId, Boolean isPrivate) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));

        // Only creator can change privacy settings
        if (!channel.getCreatedBy().equals(userId)) {
            throw new RuntimeException("Only channel creator can change privacy settings");
        }

        channel.setIsPrivate(isPrivate);
        channel = channelRepository.save(channel);

        boolean isMember = channelMemberRepository.existsByChannelIdAndUserId(channelId, userId);
        int unreadCount = calculateUnreadCount(userId, channelId);

        return ChannelResponse.builder()
                .id(channel.getId())
                .workspaceId(channel.getWorkspace().getId())
                .name(channel.getName())
                .description(channel.getDescription())
                .isPrivate(channel.getIsPrivate())
                .createdBy(channel.getCreatedBy())
                .isMember(isMember)
                .unreadCount(unreadCount)
                .createdAt(channel.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getChannelMembers(Long userId, Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));

        // Verify user is a member of the channel
        if (!channelMemberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            throw new RuntimeException("You are not a member of this channel");
        }

        List<ChannelMember> members = channelMemberRepository.findByChannelId(channelId);

        return members.stream()
                .map(member -> UserResponse.builder()
                        .id(member.getUser().getId())
                        .email(member.getUser().getEmail())
                        .name(member.getUser().getName())
                        .avatarUrl(member.getUser().getAvatarUrl())
                        .status(member.getUser().getStatus())
                        .role(member.getRole())
                        .createdAt(member.getUser().getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateMemberRole(Long requestUserId, Long channelId, Long targetUserId, String newRole) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));

        // Only creator can change member roles
        if (!channel.getCreatedBy().equals(requestUserId)) {
            throw new RuntimeException("Only channel creator can change member roles");
        }

        // Cannot change creator's role
        if (channel.getCreatedBy().equals(targetUserId)) {
            throw new RuntimeException("Cannot change channel creator's role");
        }

        ChannelMember member = channelMemberRepository.findByChannelIdAndUserId(channelId, targetUserId)
                .orElseThrow(() -> new RuntimeException("User is not a member of this channel"));

        member.setRole(newRole);
        channelMemberRepository.save(member);
    }

    private int calculateUnreadCount(Long userId, Long channelId) {
        ReadState readState = readStateRepository.findByChannelIdAndUserId(channelId, userId)
                .orElse(null);

        // Only count top-level messages (exclude thread replies)
        if (readState == null || readState.getLastReadMessageId() == null) {
            return (int) messageRepository.findByChannelIdAndParentMessageIdIsNullAndIsDeletedFalseOrderByCreatedAtAsc(channelId).stream().count();
        }

        List<Message> allMessages = messageRepository.findByChannelIdAndParentMessageIdIsNullAndIsDeletedFalseOrderByCreatedAtAsc(channelId);
        long lastReadMessageId = readState.getLastReadMessageId();

        return (int) allMessages.stream()
                .filter(m -> m.getId() > lastReadMessageId)
                .count();
    }
}
