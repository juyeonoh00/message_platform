package com.messenger.controller;

import com.messenger.dto.search.SearchRequest;
import com.messenger.dto.search.SearchResponse;
import com.messenger.repository.WorkspaceMemberRepository;
import com.messenger.service.ElasticsearchService;
import com.messenger.entity.MessageDocument;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final ElasticsearchService elasticsearchService;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @PostMapping
    public ResponseEntity<SearchResponse> searchMessages(
            Authentication authentication,
            @Valid @RequestBody SearchRequest request) {

        Long userId = (Long) authentication.getPrincipal();

        // Verify user is workspace member
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(request.getWorkspaceId(), userId)) {
            throw new RuntimeException("Not a member of this workspace");
        }

        List<MessageDocument> results = elasticsearchService.searchMessages(
                request.getWorkspaceId(),
                request.getChannelId(),
                request.getKeyword(),
                request.getStartTime(),
                request.getEndTime()
        );

        SearchResponse response = SearchResponse.builder()
                .results(results)
                .totalCount(results.size())
                .build();

        return ResponseEntity.ok(response);
    }
}
