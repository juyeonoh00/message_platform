package com.messenger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messenger.dto.message.IngestChunkRequest;
import com.messenger.dto.message.UpdateChunkRequest;
import com.messenger.dto.message.ChunkResponse;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * RAG API 클라이언트
 * http://mugtelecom.asuscomm.com:8080 에 질문을 보내고 답변을 받는 클라이언트
 */
@Slf4j
@Component
public class RagApiClient {

    private static final String RAG_API_URL = "http://221.143.23.118:8080";
    private static final int TOP_K = 1;
    private static final String LLM_TYPE = "qwen";
    private final WebClient webClient;
    private String accessToken;

    @Value("${rag.api.account-id}")
    private String accountId;

    @Value("${rag.api.password}")
    private String password;

    public RagApiClient() {
        this.webClient = WebClient.builder()
                .baseUrl(RAG_API_URL)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * 응답에서 출처 정보를 제거합니다.
     * 예: [출처: 문서_1 | rerank: -1.900] 형식의 문자열 제거
     *
     * @param text 원본 텍스트
     * @return 출처 정보가 제거된 텍스트
     */
    private String removeSourceCitations(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // [출처: ... | rerank: ...] 패턴 제거
        // 정규표현식: \[출처:[^\]]*\]
        String cleaned = text.replaceAll("\\[출처:[^\\]]*\\]", "");

        // 연속된 공백을 하나로 합치고, 앞뒤 공백 제거
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        return cleaned;
    }

    /**
     * 인증 토큰이 있는지 확인하고, 없으면 로그인을 시도합니다.
     *
     * @return 인증 성공 여부
     */
    private boolean ensureAuthenticated() {
        if (accessToken != null && !accessToken.isEmpty()) {
            return true;
        }
        return login();
    }

    /**
     * 인증 오류 발생 시 재로그인을 시도합니다.
     *
     * @return 재로그인 성공 여부
     */
    private boolean handleAuthenticationError() {
        log.warn("토큰 만료 또는 인증 실패, 재로그인 시도");
        accessToken = null;  // 토큰 초기화
        return login();
    }

    /**
     * RAG API 로그인
     * /auth/login 엔드포인트에 account_id와 password를 보내서 accessToken을 받습니다.
     *
     * @return 로그인 성공 여부
     */
    private boolean login() {
        try {
            log.info("RAG API 로그인 시도: account_id={}", accountId);

            // Request Body 생성
            Map<String, Object> loginRequest = new HashMap<>();
            loginRequest.put("account_id", accountId);
            loginRequest.put("password", password);

            // WebClient로 POST 요청
            String response = webClient.post()
                    .uri("/auth/login")
                    .bodyValue(loginRequest)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("RAG API 로그인 응답: {}", response);

            // JSON 응답에서 access_token 추출 (언더스코어 형식)
            if (response != null && !response.isEmpty()) {
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    this.accessToken = jsonResponse.optString("access_token", "");

                    if (this.accessToken != null && !this.accessToken.isEmpty()) {
                        log.info("RAG API 로그인 성공 - 토큰 획득: {}...",
                                this.accessToken.substring(0, Math.min(20, this.accessToken.length())));
                        return true;
                    } else {
                        log.error("RAG API 로그인 실패 - access_token이 응답에 없음");
                        log.error("응답 내용: {}", response);
                        return false;
                    }
                } catch (Exception e) {
                    log.error("RAG API 로그인 응답 파싱 실패: {}", e.getMessage());
                    return false;
                }
            }

            log.error("RAG API 로그인 실패 - 응답 없음");
            return false;

        } catch (Exception e) {
            log.error("RAG API 로그인 중 오류 발생: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * RAG API를 호출하여 질문에 대한 답변을 받습니다.
     *
     * @param question 질문 (프롬프트)
     * @return RAG API 응답
     */
    public String query(String question) {
        return query(question, TOP_K, LLM_TYPE);
    }

    /**
     * RAG API를 호출하여 질문에 대한 답변을 받습니다.
     *
     * @param question 질문 (프롬프트)
     * @param topK top_k 값
     * @param llmType LLM 타입
     * @return RAG API 응답
     */
    public String query(String question, int topK, String llmType) {
        // 인증 확인
        if (!ensureAuthenticated()) {
            log.error("인증 실패로 인해 RAG API 호출 불가");
            return null;
        }

        try {
            // Request Body 생성
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("question", question);
            requestBody.put("top_k", topK);
            requestBody.put("llm_type", llmType);
            requestBody.put("collection_name", Arrays.asList("message_platform", "message_platform_doc"));

            log.info("RAG API 요청: question={}, top_k={}, llm_type={}", question, topK, llmType);

            // WebClient로 POST 요청 (타임아웃 없이 응답 대기)
            String response = webClient.post()
                    .uri("/ask")  // RAG API 엔드포인트 경로
                    .header("Authorization", "Bearer " + accessToken)  // 토큰을 header에 포함
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("RAG API 전체 응답: {}", response);

            // JSON 응답에서 answer 필드만 추출
            if (response != null && !response.isEmpty()) {
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    String answer = jsonResponse.optString("answer", "");
                    log.info("RAG API answer 필드 추출 완료 - 길이: {} 자", answer.length());

                    // 출처 정보 제거 (예: [출처: 문서_1 | rerank: -1.900])
                    String cleanedAnswer = removeSourceCitations(answer);
                    log.info("출처 정보 제거 완료 - 길이: {} 자", cleanedAnswer.length());

                    return cleanedAnswer;
                } catch (Exception e) {
                    log.error("JSON 파싱 실패, 원본 응답 반환: {}", e.getMessage());
                    return response;
                }
            }

            return response;

        } catch (WebClientResponseException e) {
            // 401 또는 403 에러 시 재로그인 후 재시도
            if ((e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403)
                    && handleAuthenticationError()) {
                log.info("재로그인 성공, RAG API 재시도");
                return query(question, topK, llmType);
            }
            log.error("RAG API 호출 실패 (HTTP {}): {}", e.getStatusCode().value(), e.getMessage(), e);
            return null;
        } catch (Exception e) {
            log.error("RAG API 호출 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * RAG API에 청크 데이터를 전송합니다 (채널 스레드 및 답변).
     *
     * @param request IngestChunkRequest 데이터
     * @return chunk_id (실패 시 null)
     */
    public String ingestChunk(IngestChunkRequest request) {
        // 인증 확인
        if (!ensureAuthenticated()) {
            log.error("인증 실패로 인해 ingestChunk API 호출 불가");
            return null;
        }

        try {
            log.info("Ingest Chunk API 요청: channelId={}, threadId={}, userId={}",
                    request.getChannelId(), request.getThreadId(), request.getUserId());

            // WebClient로 POST 요청
            String response = webClient.post()
                    .uri("/ingest/chunk")
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Ingest Chunk API 응답: {}", response);

            // JSON 응답에서 chunk_id 추출
            if (response != null && !response.isEmpty()) {
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    String chunkId = jsonResponse.optString("chunk_id", null);
                    if (chunkId != null && !chunkId.isEmpty()) {
                        log.info("Ingest Chunk 성공 - chunk_id: {}", chunkId);
                        return chunkId;
                    } else {
                        log.warn("Ingest Chunk 응답에 chunk_id가 없음");
                        return null;
                    }
                } catch (Exception e) {
                    log.error("Ingest Chunk API 응답 파싱 실패: {}", e.getMessage());
                    return null;
                }
            }

            return null;

        } catch (WebClientResponseException e) {
            // 401 또는 403 에러 시 재로그인 후 재시도
            if ((e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403)
                    && handleAuthenticationError()) {
                log.info("재로그인 성공, Ingest Chunk API 재시도");
                return ingestChunk(request);
            }
            log.error("Ingest Chunk API 호출 실패 (HTTP {}): {}", e.getStatusCode().value(), e.getMessage(), e);
            return null;
        } catch (Exception e) {
            log.error("Ingest Chunk API 호출 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * RAG API의 청크를 업데이트합니다.
     *
     * @param chunkId 업데이트할 청크 ID
     * @param request UpdateChunkRequest 데이터
     * @param collectionName 컬렉션/인덱스 이름
     * @return ChunkResponse 응답
     */
    public ChunkResponse updateChunk(String chunkId, UpdateChunkRequest request, String collectionName) {
        // 인증 확인
        if (!ensureAuthenticated()) {
            log.error("인증 실패로 인해 Update Chunk API 호출 불가");
            return ChunkResponse.builder()
                    .status("error")
                    .chunkId(chunkId)
                    .message("인증 실패")
                    .build();
        }

        try {
            log.info("Update Chunk API 요청: chunkId={}, channelId={}, threadId={}",
                    chunkId, request.getChannelId(), request.getThreadId());

            // Query parameter로 collection_name 추가
            String uri = String.format("/ingest/chunk/%s?collection_name=%s", chunkId, collectionName);

            // WebClient로 PUT 요청
            String response = webClient.put()
                    .uri(uri)
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Update Chunk API 응답: {}", response);

            // JSON 응답 파싱
            if (response != null && !response.isEmpty()) {
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    return ChunkResponse.builder()
                            .status(jsonResponse.optString("status", "success"))
                            .chunkId(jsonResponse.optString("chunk_id", chunkId))
                            .message(jsonResponse.optString("message", "청크가 성공적으로 업데이트되었습니다."))
                            .build();
                } catch (Exception e) {
                    log.error("JSON 파싱 실패: {}", e.getMessage());
                    return ChunkResponse.builder()
                            .status("success")
                            .chunkId(chunkId)
                            .message("청크가 성공적으로 업데이트되었습니다.")
                            .build();
                }
            }

            return ChunkResponse.builder()
                    .status("success")
                    .chunkId(chunkId)
                    .message("청크가 성공적으로 업데이트되었습니다.")
                    .build();

        } catch (WebClientResponseException e) {
            // 401 또는 403 에러 시 재로그인 후 재시도
            if ((e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403)
                    && handleAuthenticationError()) {
                log.info("재로그인 성공, Update Chunk API 재시도");
                return updateChunk(chunkId, request, collectionName);
            }
            log.error("Update Chunk API 호출 실패 (HTTP {}): {}", e.getStatusCode().value(), e.getMessage(), e);
            return ChunkResponse.builder()
                    .status("error")
                    .chunkId(chunkId)
                    .message("청크 업데이트 실패: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Update Chunk API 호출 중 오류 발생: {}", e.getMessage(), e);
            return ChunkResponse.builder()
                    .status("error")
                    .chunkId(chunkId)
                    .message("청크 업데이트 중 오류 발생: " + e.getMessage())
                    .build();
        }
    }

    /**
     * RAG API의 청크를 삭제합니다.
     *
     * @param chunkId 삭제할 청크 ID
     * @param collectionName 컬렉션/인덱스 이름
     * @return ChunkResponse 응답
     */
    public ChunkResponse deleteChunk(String chunkId, String collectionName) {
        // 인증 확인
        if (!ensureAuthenticated()) {
            log.error("인증 실패로 인해 Delete Chunk API 호출 불가");
            return ChunkResponse.builder()
                    .status("error")
                    .chunkId(chunkId)
                    .message("인증 실패")
                    .build();
        }

        try {
            log.info("Delete Chunk API 요청: chunkId={}, collectionName={}", chunkId, collectionName);

            // Query parameter로 collection_name 추가
            String uri = String.format("/ingest/chunk/%s?collection_name=%s", chunkId, collectionName);

            // WebClient로 DELETE 요청
            String response = webClient.delete()
                    .uri(uri)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Delete Chunk API 응답: {}", response);

            // JSON 응답 파싱
            if (response != null && !response.isEmpty()) {
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    return ChunkResponse.builder()
                            .status(jsonResponse.optString("status", "success"))
                            .chunkId(jsonResponse.optString("chunk_id", chunkId))
                            .message(jsonResponse.optString("message", "청크가 성공적으로 삭제되었습니다."))
                            .build();
                } catch (Exception e) {
                    log.error("JSON 파싱 실패: {}", e.getMessage());
                    return ChunkResponse.builder()
                            .status("success")
                            .chunkId(chunkId)
                            .message("청크가 성공적으로 삭제되었습니다.")
                            .build();
                }
            }

            return ChunkResponse.builder()
                    .status("success")
                    .chunkId(chunkId)
                    .message("청크가 성공적으로 삭제되었습니다.")
                    .build();

        } catch (WebClientResponseException e) {
            // 401 또는 403 에러 시 재로그인 후 재시도
            if ((e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403)
                    && handleAuthenticationError()) {
                log.info("재로그인 성공, Delete Chunk API 재시도");
                return deleteChunk(chunkId, collectionName);
            }
            log.error("Delete Chunk API 호출 실패 (HTTP {}): {}", e.getStatusCode().value(), e.getMessage(), e);
            return ChunkResponse.builder()
                    .status("error")
                    .chunkId(chunkId)
                    .message("청크 삭제 실패: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Delete Chunk API 호출 중 오류 발생: {}", e.getMessage(), e);
            return ChunkResponse.builder()
                    .status("error")
                    .chunkId(chunkId)
                    .message("청크 삭제 중 오류 발생: " + e.getMessage())
                    .build();
        }
    }

    /**
     * RAG API에 문서 파일을 전송합니다 (인제스트).
     *
     * @param file 업로드할 파일
     * @param collectionName 컬렉션 이름
     * @return 성공 여부
     */
    public boolean ingestFile(MultipartFile file, String collectionName) {
        // 인증 확인
        if (!ensureAuthenticated()) {
            log.error("인증 실패로 인해 Ingest File API 호출 불가");
            return false;
        }

        try {
            log.info("Ingest File API 요청: fileName={}, collectionName={}",
                    file.getOriginalFilename(), collectionName);

            // MultipartBodyBuilder를 사용하여 multipart/form-data 생성
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

            // 파일 추가
            bodyBuilder.part("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });

            // collection_name 추가
            bodyBuilder.part("collection_name", collectionName);

            // WebClient로 POST 요청
            String response = webClient.post()
                    .uri("/ingest")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Ingest File API 응답: {}", response);

            if (response != null && !response.isEmpty()) {
                log.info("Ingest File 성공: fileName={}", file.getOriginalFilename());
                return true;
            }

            return false;

        } catch (WebClientResponseException e) {
            // 401 또는 403 에러 시 재로그인 후 재시도
            if ((e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403)
                    && handleAuthenticationError()) {
                log.info("재로그인 성공, Ingest File API 재시도");
                return ingestFile(file, collectionName);
            }
            log.error("Ingest File API 호출 실패 (HTTP {}): {}",
                    e.getStatusCode().value(), e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("Ingest File API 호출 중 오류 발생: {}", e.getMessage(), e);
            return false;
        }
    }
}
