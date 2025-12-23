package com.messenger.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    @NotBlank(message = "이름은 필수입니다")
    @Size(max = 100, message = "이름은 100자를 초과할 수 없습니다")
    private String name;

    @Size(max = 500, message = "프로필 이미지 URL은 500자를 초과할 수 없습니다")
    private String avatarUrl;

    @Size(max = 20, message = "상태는 20자를 초과할 수 없습니다")
    private String status;
}
