package com.messenger.controller;

import com.messenger.dto.device.DeviceResponse;
import com.messenger.dto.device.RegisterDeviceRequest;
import com.messenger.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /**
     * 디바이스 등록 또는 업데이트
     */
    @PostMapping("/register")
    public ResponseEntity<DeviceResponse> registerDevice(
            Authentication authentication,
            @Valid @RequestBody RegisterDeviceRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("디바이스 등록 요청: userId={}, deviceType={}", userId, request.getDeviceType());

        DeviceResponse response = deviceService.registerDevice(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 디바이스 Heartbeat (활동 상태 업데이트)
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(
            Authentication authentication,
            @RequestParam String deviceToken) {
        Long userId = (Long) authentication.getPrincipal();
        deviceService.updateHeartbeat(userId, deviceToken);
        return ResponseEntity.ok().build();
    }

    /**
     * 디바이스 비활성화
     */
    @PostMapping("/deactivate")
    public ResponseEntity<Void> deactivateDevice(
            Authentication authentication,
            @RequestParam String deviceToken) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("디바이스 비활성화 요청: userId={}, deviceToken={}", userId, deviceToken);

        deviceService.deactivateDevice(userId, deviceToken);
        return ResponseEntity.ok().build();
    }

    /**
     * 사용자의 활성 디바이스 목록 조회
     */
    @GetMapping("/active")
    public ResponseEntity<List<DeviceResponse>> getActiveDevices(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<DeviceResponse> devices = deviceService.getActiveDevices(userId);
        return ResponseEntity.ok(devices);
    }
}
