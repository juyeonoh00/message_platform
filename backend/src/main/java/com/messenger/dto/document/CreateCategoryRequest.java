package com.messenger.dto.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCategoryRequest {
    @NotBlank(message = "카테고리 이름은 필수입니다")
    private String name;

    @NotNull(message = "워크스페이스 ID는 필수입니다")
    private Long workspaceId;
}
