package com.documents.support;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.documents.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DocumentSortKeyGenerator {

    private static final int SORT_KEY_WIDTH = 20;

    private final DocumentRepository documentRepository;

    public String genNextSortKey(UUID workspaceId, UUID parentId) {
        long nextOrder = documentRepository.findMaxSortKeyByWorkspaceIdAndParentId(workspaceId, parentId)
                .map(this::parseSortKey)
                .map(currentMax -> currentMax + 1L)
                .orElse(1L);

		// TODO: SORT_KEY 생성 전략은 추후 변경될 수 있음
        return String.format(Locale.ROOT, "%0" + SORT_KEY_WIDTH + "d", nextOrder);
    }

    private long parseSortKey(String sortKey) {
        try {
            return Long.parseLong(sortKey);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Document sortKey must be a numeric string.", ex);
        }
    }
}
