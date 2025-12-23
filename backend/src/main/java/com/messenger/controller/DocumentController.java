package com.messenger.controller;

import com.messenger.dto.document.CreateCategoryRequest;
import com.messenger.dto.document.DocumentCategoryResponse;
import com.messenger.dto.document.DocumentResponse;
import com.messenger.service.DocumentService;
import com.messenger.service.FileStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final FileStorageService fileStorageService;

    /**
     * 워크스페이스의 모든 카테고리와 문서를 조회합니다.
     */
    @GetMapping("/workspace/{workspaceId}")
    public ResponseEntity<List<DocumentCategoryResponse>> getCategoriesWithDocuments(
            @PathVariable Long workspaceId) {
        List<DocumentCategoryResponse> response = documentService.getCategoriesWithDocuments(workspaceId);
        return ResponseEntity.ok(response);
    }

    /**
     * 새로운 카테고리를 생성합니다.
     */
    @PostMapping("/categories")
    public ResponseEntity<DocumentCategoryResponse> createCategory(
            @Valid @RequestBody CreateCategoryRequest request) {
        DocumentCategoryResponse response = documentService.createCategory(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 카테고리를 삭제합니다.
     */
    @DeleteMapping("/categories/{categoryId}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long categoryId) {
        documentService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 문서를 업로드합니다.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            Authentication authentication,
            @RequestParam Long categoryId,
            @RequestParam Long workspaceId,
            @RequestParam MultipartFile file) {

        Long userId = (Long) authentication.getPrincipal();

        DocumentResponse response = documentService.uploadDocument(
                categoryId, workspaceId, userId, file);

        return ResponseEntity.ok(response);
    }

    /**
     * 문서를 다운로드합니다.
     */
    @GetMapping("/{documentId}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable Long documentId) {
        DocumentResponse document = documentService.getDocument(documentId);

        InputStream inputStream = fileStorageService.downloadDocument(document.getFileUrl());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.getName() + "\"");
        headers.setContentType(MediaType.parseMediaType(document.getContentType()));

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(inputStream));
    }

    /**
     * 문서를 삭제합니다.
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long documentId) {
        documentService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 문서 정보를 조회합니다.
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable Long documentId) {
        DocumentResponse response = documentService.getDocument(documentId);
        return ResponseEntity.ok(response);
    }
}
