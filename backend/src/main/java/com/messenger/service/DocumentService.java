package com.messenger.service;

import com.messenger.dto.document.CreateCategoryRequest;
import com.messenger.dto.document.DocumentCategoryResponse;
import com.messenger.dto.document.DocumentResponse;
import com.messenger.entity.Document;
import com.messenger.entity.DocumentCategory;
import com.messenger.entity.User;
import com.messenger.repository.DocumentCategoryRepository;
import com.messenger.repository.DocumentRepository;
import com.messenger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final RagApiClient ragApiClient;

    /**
     * 워크스페이스의 모든 카테고리와 문서를 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<DocumentCategoryResponse> getCategoriesWithDocuments(Long workspaceId) {
        List<DocumentCategory> categories = categoryRepository.findByWorkspace_Id(workspaceId);

        return categories.stream()
                .map(category -> {
                    List<Document> documents = documentRepository.findByCategory_Id(category.getId());
                    List<DocumentResponse> documentResponses = documents.stream()
                            .map(doc -> {
                                String uploaderName = doc.getUploader() != null ? doc.getUploader().getName() : "Unknown";
                                return DocumentResponse.from(doc, uploaderName);
                            })
                            .collect(Collectors.toList());

                    return DocumentCategoryResponse.from(category, documentResponses);
                })
                .collect(Collectors.toList());
    }

    /**
     * 새로운 카테고리를 생성합니다.
     */
    @Transactional
    public DocumentCategoryResponse createCategory(CreateCategoryRequest request) {
        DocumentCategory category = DocumentCategory.builder()
                .name(request.getName())
                .workspace(com.messenger.entity.Workspace.builder().id(request.getWorkspaceId()).build())
                .build();

        category = categoryRepository.save(category);

        return DocumentCategoryResponse.from(category, List.of());
    }

    /**
     * 카테고리를 삭제합니다 (해당 카테고리의 모든 문서도 함께 삭제).
     */
    @Transactional
    public void deleteCategory(Long categoryId) {
        // 카테고리의 모든 문서 삭제
        List<Document> documents = documentRepository.findByCategory_Id(categoryId);
        for (Document document : documents) {
            fileStorageService.deleteDocument(document.getFileUrl());
            documentRepository.delete(document);
        }

        // 카테고리 삭제
        categoryRepository.deleteById(categoryId);
    }

    /**
     * 문서를 업로드합니다.
     */
    @Transactional
    public DocumentResponse uploadDocument(
            Long categoryId,
            Long workspaceId,
            Long uploaderId,
            MultipartFile file) {

        // 파일을 MinIO에 업로드
        String fileUrl = fileStorageService.uploadDocument(file, file.getOriginalFilename());

        // 문서 정보를 DB에 저장
        Document document = Document.builder()
                .name(file.getOriginalFilename())
                .fileUrl(fileUrl)
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .category(DocumentCategory.builder().id(categoryId).build())
                .workspace(com.messenger.entity.Workspace.builder().id(workspaceId).build())
                .uploader(User.builder().id(uploaderId).build())
                .build();

        document = documentRepository.save(document);

        // RAG API에 문서 파일 전송 (인제스트)
        try {
            log.info("문서를 RAG API로 전송 시작: documentId={}, fileName={}",
                    document.getId(), file.getOriginalFilename());
            boolean success = ragApiClient.ingestFile(file, "message_platform_doc");
            if (success) {
                log.info("RAG API 문서 인제스트 성공: documentId={}", document.getId());
            } else {
                log.warn("RAG API 문서 인제스트 실패: documentId={}", document.getId());
            }
        } catch (Exception e) {
            log.error("RAG API 문서 인제스트 중 오류 발생: documentId={}, error={}",
                    document.getId(), e.getMessage(), e);
            // 에러가 발생해도 문서 저장은 계속 진행
        }

        // 업로더 정보 조회
        String uploaderName = document.getUploader() != null ? document.getUploader().getName() : "Unknown";

        return DocumentResponse.from(document, uploaderName);
    }

    /**
     * 문서를 삭제합니다.
     */
    @Transactional
    public void deleteDocument(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("문서를 찾을 수 없습니다"));

        // MinIO에서 파일 삭제
        fileStorageService.deleteDocument(document.getFileUrl());

        // DB에서 문서 정보 삭제
        documentRepository.delete(document);
    }

    /**
     * 문서 정보를 조회합니다.
     */
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("문서를 찾을 수 없습니다"));

        String uploaderName = document.getUploader() != null ? document.getUploader().getName() : "Unknown";

        return DocumentResponse.from(document, uploaderName);
    }
}
