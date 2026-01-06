package com.messenger.service;

import com.messenger.dto.workspace.*;
import com.messenger.entity.*;
import com.messenger.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final ChannelRepository channelRepository;
    private final MessageRepository messageRepository;
    private final RagApiClient ragApiClient;
    private final ElasticsearchService elasticsearchService;

    @Transactional
    public WorkspaceResponse createWorkspace(Long userId, CreateWorkspaceRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Workspace workspace = Workspace.builder()
                .name(request.getName())
                .description(request.getDescription())
                .ownerId(userId)
                .build();

        workspace = workspaceRepository.save(workspace);

        // Add creator as owner
        WorkspaceMember member = WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role("owner")
                .build();

        workspaceMemberRepository.save(member);

        return WorkspaceResponse.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .description(workspace.getDescription())
                .ownerId(workspace.getOwnerId())
                .role("owner")
                .createdAt(workspace.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> getUserWorkspaces(Long userId) {
        List<WorkspaceMember> memberships = workspaceMemberRepository.findByUserId(userId);

        return memberships.stream()
                .map(member -> WorkspaceResponse.builder()
                        .id(member.getWorkspace().getId())
                        .name(member.getWorkspace().getName())
                        .description(member.getWorkspace().getDescription())
                        .ownerId(member.getWorkspace().getOwnerId())
                        .role(member.getRole())
                        .createdAt(member.getWorkspace().getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(Long userId, Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("Workspace not found"));

        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new RuntimeException("Not a member of this workspace"));

        return WorkspaceResponse.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .description(workspace.getDescription())
                .ownerId(workspace.getOwnerId())
                .role(member.getRole())
                .createdAt(workspace.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> getWorkspaceMembers(Long userId, Long workspaceId) {
        // Verify user is workspace member
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new RuntimeException("Not a member of this workspace");
        }

        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspaceId);

        return members.stream()
                .map(member -> WorkspaceMemberResponse.builder()
                        .id(member.getId())
                        .userId(member.getUser().getId())
                        .userName(member.getUser().getName())
                        .userEmail(member.getUser().getEmail())
                        .userAvatarUrl(member.getUser().getAvatarUrl())
                        .role(member.getRole())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void addMember(Long userId, Long workspaceId, AddMemberRequest request) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("Workspace not found"));

        // Check if requester is owner or admin
        WorkspaceMember requester = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new RuntimeException("Not authorized"));

        if (!"owner".equals(requester.getRole()) && !"admin".equals(requester.getRole())) {
            throw new RuntimeException("Not authorized to add members");
        }

        // Find user by email
        User newUser = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User with email '" + request.getEmail() + "' not found"));

        if (workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, newUser.getId())) {
            throw new RuntimeException("User is already a member");
        }

        WorkspaceMember member = WorkspaceMember.builder()
                .workspace(workspace)
                .user(newUser)
                .role(request.getRole())
                .build();

        workspaceMemberRepository.save(member);
    }

    @Transactional
    public void removeMember(Long userId, Long workspaceId, Long memberUserId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("Workspace not found"));

        // Check if requester is owner or admin
        WorkspaceMember requester = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new RuntimeException("Not authorized"));

        if (!"owner".equals(requester.getRole()) && !"admin".equals(requester.getRole())) {
            throw new RuntimeException("Not authorized to remove members");
        }

        if (workspace.getOwnerId().equals(memberUserId)) {
            throw new RuntimeException("Cannot remove workspace owner");
        }

        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, memberUserId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        workspaceMemberRepository.delete(member);
    }

    @Transactional
    public void deleteWorkspace(Long userId, Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("Workspace not found"));

        // Only owner can delete workspace
        if (!workspace.getOwnerId().equals(userId)) {
            throw new RuntimeException("Only the workspace owner can delete this workspace");
        }

        // Delete all messages from RAG API (also deletes from Elasticsearch) before CASCADE deletion
        List<Channel> channels = channelRepository.findByWorkspaceId(workspaceId);
        for (Channel channel : channels) {
            // Delete from RAG API
            List<Message> messages = messageRepository.findByChannelId(channel.getId());
            messages.forEach(msg -> {
                if (msg.getChunkId() != null && !msg.getChunkId().isEmpty()) {
                    try {
                        ragApiClient.deleteChunk(msg.getChunkId(), "message_platform");
                        log.debug("Deleted chunk from RAG API: chunkId={}", msg.getChunkId());
                    } catch (Exception e) {
                        log.error("Failed to delete chunk from RAG API: chunkId={}", msg.getChunkId(), e);
                    }
                }
            });
        }

        // CASCADE will automatically delete:
        // - workspace_members
        // - channels (and their messages, members, read_states)
        // - document_categories (and documents)
        workspaceRepository.delete(workspace);
    }
}
