package com.messenger.dto.document;

import com.messenger.entity.DocumentCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentCategoryResponse {
    private Long id;
    private String name;
    private Long workspaceId;
    private LocalDateTime createdAt;
    private List<DocumentResponse> documents;

    public static DocumentCategoryResponse from(DocumentCategory category, List<DocumentResponse> documents) {
        return DocumentCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .workspaceId(category.getWorkspaceId())
                .createdAt(category.getCreatedAt())
                .documents(documents)
                .build();
    }
}
