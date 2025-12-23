package com.messenger.dto.channel;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateChannelPrivacyRequest {
    @NotNull
    private Boolean isPrivate;
}
