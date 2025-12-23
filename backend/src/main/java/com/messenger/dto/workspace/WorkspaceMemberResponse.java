package com.messenger.dto.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceMemberResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String userAvatarUrl;
    private String role;
}
