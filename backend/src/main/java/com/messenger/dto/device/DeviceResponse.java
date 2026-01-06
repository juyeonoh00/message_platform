package com.messenger.dto.device;

import com.messenger.entity.DeviceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceResponse {
    private Long id;
    private String deviceToken;
    private DeviceType deviceType;
    private String platform;
    private Boolean isActive;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
}
