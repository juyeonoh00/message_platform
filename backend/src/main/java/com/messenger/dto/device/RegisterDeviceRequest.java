package com.messenger.dto.device;

import com.messenger.entity.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDeviceRequest {

    @NotBlank(message = "Device token is required")
    private String deviceToken;

    @NotNull(message = "Device type is required")
    private DeviceType deviceType;

    private String platform; // WINDOWS, MAC, LINUX, CHROME, FIREFOX, etc.
}
