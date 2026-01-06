package com.messenger.controller;

import com.messenger.dto.workspace.*;
import com.messenger.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    public ResponseEntity<WorkspaceResponse> createWorkspace(
            Authentication authentication,
            @Valid @RequestBody CreateWorkspaceRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        WorkspaceResponse response = workspaceService.createWorkspace(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceResponse>> getUserWorkspaces(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<WorkspaceResponse> workspaces = workspaceService.getUserWorkspaces(userId);
        return ResponseEntity.ok(workspaces);
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> getWorkspace(
            Authentication authentication,
            @PathVariable Long workspaceId) {
        Long userId = (Long) authentication.getPrincipal();
        WorkspaceResponse workspace = workspaceService.getWorkspace(userId, workspaceId);
        return ResponseEntity.ok(workspace);
    }

    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<List<WorkspaceMemberResponse>> getWorkspaceMembers(
            Authentication authentication,
            @PathVariable Long workspaceId) {
        Long userId = (Long) authentication.getPrincipal();
        List<WorkspaceMemberResponse> members = workspaceService.getWorkspaceMembers(userId, workspaceId);
        return ResponseEntity.ok(members);
    }

    @PostMapping("/{workspaceId}/members")
    public ResponseEntity<Void> addMember(
            Authentication authentication,
            @PathVariable Long workspaceId,
            @Valid @RequestBody AddMemberRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        workspaceService.addMember(userId, workspaceId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{workspaceId}/members/{memberUserId}")
    public ResponseEntity<Void> removeMember(
            Authentication authentication,
            @PathVariable Long workspaceId,
            @PathVariable Long memberUserId) {
        Long userId = (Long) authentication.getPrincipal();
        workspaceService.removeMember(userId, workspaceId, memberUserId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<Void> deleteWorkspace(
            Authentication authentication,
            @PathVariable Long workspaceId) {
        Long userId = (Long) authentication.getPrincipal();
        workspaceService.deleteWorkspace(userId, workspaceId);
        return ResponseEntity.ok().build();
    }
}
