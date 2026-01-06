package com.messenger.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.messenger.entity.Message;
import com.messenger.entity.MessageDocument;
import com.messenger.entity.User;
import com.messenger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchService {

    private static final String INDEX_NAME = "message_platform";

    private final ElasticsearchClient elasticsearchClient;

    public List<MessageDocument> searchMessages(Long workspaceId, Long channelId,
                                                String keyword, LocalDateTime startTime,
                                                LocalDateTime endTime) {
        try {
            List<Query> queries = new ArrayList<>();

//            // Workspace filter
//            queries.add(Query.of(q -> q.term(t -> t
//                    .field("workspaceId")
//                    .value(workspaceId)
//            )));
//
//            // Channel filter (optional)
//            if (channelId != null) {
//                queries.add(Query.of(q -> q.term(t -> t
//                        .field("channelId")
//                        .value(channelId)
//                )));
//            }

            // Keyword search
            if (keyword != null && !keyword.isEmpty()) {
                queries.add(Query.of(q -> q.match(m -> m
                        .field("text")
                        .query(keyword)
                )));
            }
//
//            // Time range filter
//            if (startTime != null && endTime != null) {
//                queries.add(Query.of(q -> q.range(r -> r
//                        .field("createdAt")
//                        .gte(co.elastic.clients.json.JsonData.of(startTime.toString()))
//                        .lte(co.elastic.clients.json.JsonData.of(endTime.toString()))
//                )));
//            }

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .query(q -> q.bool(b -> b.must(queries)))
                    .size(50)
            );

            SearchResponse<MessageDocument> response = elasticsearchClient.search(
                    searchRequest,
                    MessageDocument.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .collect(Collectors.toList());

        } catch (IOException e) {
            log.error("Error searching messages", e);
            return new ArrayList<>();
        }
    }
}
