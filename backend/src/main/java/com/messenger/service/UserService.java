package com.messenger.service;

import com.messenger.dto.user.UpdateProfileRequest;
import com.messenger.dto.user.UserResponse;
import com.messenger.entity.User;
import com.messenger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public UserResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        return convertToResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        // 프로필 업데이트
        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        User updatedUser = userRepository.save(user);
        return convertToResponse(updatedUser);
    }

    @Transactional
    public UserResponse updateProfileWithAvatar(Long userId, String name, String status, MultipartFile avatar) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        // 이름 업데이트
        if (name != null && !name.isBlank()) {
            user.setName(name);
        }

        // 상태 업데이트
        if (status != null && !status.isBlank()) {
            user.setStatus(status);
        }

        // 프로필 이미지 업데이트
        if (avatar != null && !avatar.isEmpty()) {
            // 기존 이미지 삭제 (MinIO에서)
            String oldAvatarUrl = user.getAvatarUrl();
            if (oldAvatarUrl != null && !oldAvatarUrl.isEmpty()) {
                try {
                    fileStorageService.deleteFile(oldAvatarUrl);
                } catch (Exception e) {
                    // 기존 이미지 삭제 실패는 무시 (로그는 서비스에서 처리)
                }
            }

            // 새 이미지 업로드
            String newAvatarUrl = fileStorageService.uploadFile(avatar);
            user.setAvatarUrl(newAvatarUrl);
        }

        User updatedUser = userRepository.save(user);
        return convertToResponse(updatedUser);
    }

    private UserResponse convertToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
