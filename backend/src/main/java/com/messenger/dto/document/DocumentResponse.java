package com.messenger.dto.document;

import com.messenger.entity.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {
    private Long id;
    private String name;
    private String fileUrl;
    private Long fileSize;
    private String contentType;
    private Long categoryId;
    private Long workspaceId;
    private Long uploaderId;
    private String uploaderName;
    private LocalDateTime uploadedAt;

    public static DocumentResponse from(Document document, String uploaderName) {
        return DocumentResponse.builder()
                .id(document.getId())
                .name(document.getName())
                .fileUrl(document.getFileUrl())
                .fileSize(document.getFileSize())
                .contentType(document.getContentType())
                .categoryId(document.getCategory().getId())
                .workspaceId(document.getWorkspace().getId())
                .uploaderId(document.getUploader().getId())
                .uploaderName(uploaderName)
                .uploadedAt(document.getUploadedAt())
                .build();
    }
}
