package com.messenger.controller;

import com.messenger.dto.ai.AiChatHistoryResponse;
import com.messenger.dto.ai.AiChatRequest;
import com.messenger.dto.ai.AiChatResponse;
import com.messenger.entity.AiChatHistory;
import com.messenger.repository.AiChatHistoryRepository;
import com.messenger.service.RagApiClient;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final RagApiClient ragApiClient;
    private final AiChatHistoryRepository aiChatHistoryRepository;

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(
            Authentication authentication,
            @Valid @RequestBody AiChatRequest request) {

        Long userId = (Long) authentication.getPrincipal();
        log.info("AI 챗봇 요청 - userId: {}, question: {}", userId, request.getQuestion());

        // Save user question to history
        AiChatHistory userMessage = AiChatHistory.builder()
                .userId(userId)
                .role("user")
                .content(request.getQuestion())
                .build();
        aiChatHistoryRepository.save(userMessage);

        try {
            // RAG API 호출
            String answer;
            if (request.getTopK() != null && request.getLlmType() != null) {
                answer = ragApiClient.query(request.getQuestion(), request.getTopK(), request.getLlmType());
            } else {
                answer = ragApiClient.query(request.getQuestion());
            }

            if (answer != null && !answer.isEmpty()) {
                log.info("AI 챗봇 응답 성공 - userId: {}, answer length: {}", userId, answer.length());

                // Save AI answer to history
                AiChatHistory aiMessage = AiChatHistory.builder()
                        .userId(userId)
                        .role("ai")
                        .content(answer)
                        .build();
                aiChatHistoryRepository.save(aiMessage);

                return ResponseEntity.ok(AiChatResponse.builder()
                        .answer(answer)
                        .build());
            } else {
                log.error("AI 챗봇 응답 실패 - userId: {}, answer is null or empty", userId);
                String errorAnswer = "죄송합니다. 응답을 생성할 수 없습니다. 잠시 후 다시 시도해주세요.";

                // Save error message to history
                AiChatHistory aiMessage = AiChatHistory.builder()
                        .userId(userId)
                        .role("ai")
                        .content(errorAnswer)
                        .build();
                aiChatHistoryRepository.save(aiMessage);

                return ResponseEntity.ok(AiChatResponse.builder()
                        .answer(errorAnswer)
                        .error("RAG API returned null or empty response")
                        .build());
            }

        } catch (Exception e) {
            log.error("AI 챗봇 요청 처리 중 오류 발생 - userId: {}, error: {}", userId, e.getMessage(), e);
            String errorAnswer = "죄송합니다. 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";

            // Save error message to history
            AiChatHistory aiMessage = AiChatHistory.builder()
                    .userId(userId)
                    .role("ai")
                    .content(errorAnswer)
                    .build();
            aiChatHistoryRepository.save(aiMessage);

            return ResponseEntity.ok(AiChatResponse.builder()
                    .answer(errorAnswer)
                    .error(e.getMessage())
                    .build());
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<AiChatHistoryResponse>> getHistory(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("AI 챗봇 히스토리 조회 - userId: {}", userId);

        List<AiChatHistory> history = aiChatHistoryRepository.findByUserIdOrderByCreatedAtAsc(userId);

        List<AiChatHistoryResponse> response = history.stream()
                .map(h -> AiChatHistoryResponse.builder()
                        .id(h.getId())
                        .role(h.getRole())
                        .content(h.getContent())
                        .createdAt(h.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}
