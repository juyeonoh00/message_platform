package com.messenger.dto.channel;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateChannelMemberRoleRequest {
    @NotBlank(message = "Role is required")
    private String role;
}
