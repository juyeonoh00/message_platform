package com.messenger.dto.search;

import com.messenger.entity.MessageDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {
    private List<MessageDocument> results;
    private int totalCount;
}
