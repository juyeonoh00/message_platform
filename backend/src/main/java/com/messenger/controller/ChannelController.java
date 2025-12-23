package com.messenger.controller;

import com.messenger.dto.channel.*;
import com.messenger.dto.user.UserResponse;
import com.messenger.service.ChannelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @PostMapping
    public ResponseEntity<ChannelResponse> createChannel(
            Authentication authentication,
            @Valid @RequestBody CreateChannelRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        ChannelResponse response = channelService.createChannel(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/workspace/{workspaceId}")
    public ResponseEntity<List<ChannelResponse>> getWorkspaceChannels(
            Authentication authentication,
            @PathVariable Long workspaceId) {
        Long userId = (Long) authentication.getPrincipal();
        List<ChannelResponse> channels = channelService.getWorkspaceChannels(userId, workspaceId);
        return ResponseEntity.ok(channels);
    }

    @PostMapping("/{channelId}/join")
    public ResponseEntity<Void> joinChannel(
            Authentication authentication,
            @PathVariable Long channelId) {
        Long userId = (Long) authentication.getPrincipal();
        channelService.joinChannel(userId, channelId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{channelId}/leave")
    public ResponseEntity<Void> leaveChannel(
            Authentication authentication,
            @PathVariable Long channelId) {
        Long userId = (Long) authentication.getPrincipal();
        channelService.leaveChannel(userId, channelId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{channelId}/members")
    public ResponseEntity<Void> addMemberToChannel(
            Authentication authentication,
            @PathVariable Long channelId,
            @Valid @RequestBody AddChannelMemberRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        channelService.addMemberToChannel(userId, channelId, request.getUserId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{channelId}")
    public ResponseEntity<Void> deleteChannel(
            Authentication authentication,
            @PathVariable Long channelId) {
        Long userId = (Long) authentication.getPrincipal();
        channelService.deleteChannel(userId, channelId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{channelId}/name")
    public ResponseEntity<ChannelResponse> updateChannelName(
            Authentication authentication,
            @PathVariable Long channelId,
            @Valid @RequestBody UpdateChannelNameRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        ChannelResponse response = channelService.updateChannelName(userId, channelId, request.getName());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{channelId}/members/{userId}")
    public ResponseEntity<Void> removeMemberFromChannel(
            Authentication authentication,
            @PathVariable Long channelId,
            @PathVariable Long userId) {
        Long requestUserId = (Long) authentication.getPrincipal();
        channelService.removeMemberFromChannel(requestUserId, channelId, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{channelId}/privacy")
    public ResponseEntity<ChannelResponse> updateChannelPrivacy(
            Authentication authentication,
            @PathVariable Long channelId,
            @Valid @RequestBody UpdateChannelPrivacyRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        ChannelResponse response = channelService.updateChannelPrivacy(userId, channelId, request.getIsPrivate());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{channelId}/members")
    public ResponseEntity<List<UserResponse>> getChannelMembers(
            Authentication authentication,
            @PathVariable Long channelId) {
        Long userId = (Long) authentication.getPrincipal();
        List<UserResponse> members = channelService.getChannelMembers(userId, channelId);
        return ResponseEntity.ok(members);
    }

    @PutMapping("/{channelId}/members/{userId}/role")
    public ResponseEntity<Void> updateMemberRole(
            Authentication authentication,
            @PathVariable Long channelId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateChannelMemberRoleRequest request) {
        Long requestUserId = (Long) authentication.getPrincipal();
        channelService.updateMemberRole(requestUserId, channelId, userId, request.getRole());
        return ResponseEntity.ok().build();
    }
}
