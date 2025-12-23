package com.messenger.dto.channel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateChannelNameRequest {
    @NotBlank
    @Size(max = 100)
    private String name;
}
