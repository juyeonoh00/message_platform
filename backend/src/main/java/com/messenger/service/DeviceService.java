package com.messenger.service;

import com.messenger.dto.device.DeviceResponse;
import com.messenger.dto.device.RegisterDeviceRequest;
import com.messenger.entity.DeviceType;
import com.messenger.entity.User;
import com.messenger.entity.UserDevice;
import com.messenger.repository.UserDeviceRepository;
import com.messenger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final UserDeviceRepository deviceRepository;
    private final UserRepository userRepository;

    /**
     * 디바이스 등록 또는 업데이트
     */
    @Transactional
    public DeviceResponse registerDevice(Long userId, RegisterDeviceRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        // 기존 디바이스 토큰으로 조회
        UserDevice device = deviceRepository.findByUserAndDeviceToken(user, request.getDeviceToken())
                .orElse(null);

        if (device != null) {
            // 기존 디바이스 업데이트
            device.setDeviceType(request.getDeviceType());
            device.setPlatform(request.getPlatform());
            device.updateLastActive();
            log.info("디바이스 업데이트: userId={}, deviceType={}, platform={}",
                    userId, request.getDeviceType(), request.getPlatform());
        } else {
            // 새 디바이스 등록
            device = UserDevice.builder()
                    .user(user)
                    .deviceToken(request.getDeviceToken())
                    .deviceType(request.getDeviceType())
                    .platform(request.getPlatform())
                    .isActive(true)
                    .lastActiveAt(LocalDateTime.now())
                    .build();
            log.info("새 디바이스 등록: userId={}, deviceType={}, platform={}",
                    userId, request.getDeviceType(), request.getPlatform());
        }

        UserDevice savedDevice = deviceRepository.save(device);
        return convertToResponse(savedDevice);
    }

    /**
     * 디바이스 Heartbeat (활동 상태 업데이트)
     */
    @Transactional
    public void updateHeartbeat(Long userId, String deviceToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        deviceRepository.findByUserAndDeviceToken(user, deviceToken)
                .ifPresent(device -> {
                    device.updateLastActive();
                    deviceRepository.save(device);
                    log.debug("디바이스 heartbeat 업데이트: userId={}, deviceToken={}", userId, deviceToken);
                });
    }

    /**
     * 디바이스 비활성화
     */
    @Transactional
    public void deactivateDevice(Long userId, String deviceToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        deviceRepository.findByUserAndDeviceToken(user, deviceToken)
                .ifPresent(device -> {
                    device.deactivate();
                    deviceRepository.save(device);
                    log.info("디바이스 비활성화: userId={}, deviceToken={}", userId, deviceToken);
                });
    }

    /**
     * 사용자의 모든 활성 디바이스 조회
     */
    @Transactional(readOnly = true)
    public List<DeviceResponse> getActiveDevices(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        return deviceRepository.findByUserAndIsActiveTrue(user).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 사용자의 특정 타입 활성 디바이스 조회
     */
    @Transactional(readOnly = true)
    public List<UserDevice> getActiveDevicesByType(User user, DeviceType deviceType) {
        return deviceRepository.findByUserAndDeviceTypeAndIsActiveTrue(user, deviceType);
    }

    /**
     * 사용자의 모든 활성 디바이스 조회 (엔티티 반환)
     */
    @Transactional(readOnly = true)
    public List<UserDevice> getActiveDevicesEntities(User user) {
        return deviceRepository.findByUserAndIsActiveTrue(user);
    }

    /**
     * 오래된 비활성 디바이스 정리 (30일 이상 활동 없음)
     */
    @Transactional
    public void cleanupInactiveDevices() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        deviceRepository.deleteInactiveDevices(threshold);
        log.info("비활성 디바이스 정리 완료: threshold={}", threshold);
    }

    /**
     * UserDevice를 DeviceResponse로 변환
     */
    private DeviceResponse convertToResponse(UserDevice device) {
        return DeviceResponse.builder()
                .id(device.getId())
                .deviceToken(device.getDeviceToken())
                .deviceType(device.getDeviceType())
                .platform(device.getPlatform())
                .isActive(device.getIsActive())
                .lastActiveAt(device.getLastActiveAt())
                .createdAt(device.getCreatedAt())
                .build();
    }
}
