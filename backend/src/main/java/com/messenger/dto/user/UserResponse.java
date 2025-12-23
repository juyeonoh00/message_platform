package com.messenger.dto.user;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private Long id;
    private String email;
    private String name;
    private String avatarUrl;
    private String status;
    private String role;
    private LocalDateTime createdAt;
}
