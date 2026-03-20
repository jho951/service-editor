package com.documents.support;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.documents.domain.Document;
import com.documents.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DocumentSortKeyGenerator {

    private static final int SORT_KEY_WIDTH = 20;
    private static final long FIRST_SORT_KEY = 1L;

    private final DocumentRepository documentRepository;

    public String genNextSortKey(UUID workspaceId, UUID parentId) {
        long nextOrder = documentRepository.findMaxSortKeyByWorkspaceIdAndParentId(workspaceId, parentId)
                .map(this::parseSortKey)
                .map(currentMax -> currentMax + 1L)
                .orElse(1L);

		// TODO: SORT_KEY 생성 전략은 추후 변경될 수 있음
        return String.format(Locale.ROOT, "%0" + SORT_KEY_WIDTH + "d", nextOrder);
    }

    public String generate(
            List<Document> siblings,
            UUID movedDocumentId,
            UUID afterDocumentId,
            UUID beforeDocumentId
    ) {
        List<Document> activeSiblings = siblings.stream()
                .filter(document -> !document.getId().equals(movedDocumentId))
                .toList();

        if (afterDocumentId != null && beforeDocumentId != null && afterDocumentId.equals(beforeDocumentId)) {
            throw new IllegalArgumentException("afterDocumentId와 beforeDocumentId는 같은 값을 가리킬 수 없습니다.");
        }

        int afterIndex = indexOf(activeSiblings, afterDocumentId);
        int beforeIndex = indexOf(activeSiblings, beforeDocumentId);

        if (afterDocumentId != null && afterIndex < 0) {
            throw new IllegalArgumentException("afterDocumentId는 같은 부모 집합의 활성 형제 문서를 가리켜야 합니다.");
        }

        if (beforeDocumentId != null && beforeIndex < 0) {
            throw new IllegalArgumentException("beforeDocumentId는 같은 부모 집합의 활성 형제 문서를 가리켜야 합니다.");
        }

        if (afterDocumentId != null && beforeDocumentId != null && afterIndex + 1 != beforeIndex) {
            throw new IllegalArgumentException("afterDocumentId와 beforeDocumentId는 같은 삽입 간격을 가리켜야 합니다.");
        }

        if (afterIndex < 0 && beforeIndex < 0) {
            return activeSiblings.isEmpty()
                    ? format(FIRST_SORT_KEY)
                    : nextAfter(parseSortKey(activeSiblings.get(activeSiblings.size() - 1).getSortKey()));
        }

        if (afterIndex >= 0 && beforeIndex >= 0) {
            return between(
                    parseSortKey(activeSiblings.get(afterIndex).getSortKey()),
                    parseSortKey(activeSiblings.get(beforeIndex).getSortKey())
            );
        }

        if (afterIndex >= 0) {
            if (afterIndex + 1 >= activeSiblings.size()) {
                return nextAfter(parseSortKey(activeSiblings.get(afterIndex).getSortKey()));
            }

            return between(
                    parseSortKey(activeSiblings.get(afterIndex).getSortKey()),
                    parseSortKey(activeSiblings.get(afterIndex + 1).getSortKey())
            );
        }

        if (beforeIndex == 0) {
            return before(parseSortKey(activeSiblings.get(beforeIndex).getSortKey()));
        }

        return between(
                parseSortKey(activeSiblings.get(beforeIndex - 1).getSortKey()),
                parseSortKey(activeSiblings.get(beforeIndex).getSortKey())
        );
    }

    private long parseSortKey(String sortKey) {
        try {
            return Long.parseLong(sortKey);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Document sortKey must be a numeric string.", ex);
        }
    }

    private int indexOf(List<Document> siblings, UUID documentId) {
        if (documentId == null) {
            return -1;
        }

        for (int i = 0; i < siblings.size(); i++) {
            if (documentId.equals(siblings.get(i).getId())) {
                return i;
            }
        }

        return -1;
    }

    private String nextAfter(long currentSortKey) {
        if (currentSortKey == Long.MAX_VALUE) {
            throw new SortKeyRebalanceRequiredException();
        }

        return format(currentSortKey + 1L);
    }

    private String before(long currentSortKey) {
        if (currentSortKey <= FIRST_SORT_KEY) {
            throw new SortKeyRebalanceRequiredException();
        }

        return format(currentSortKey - 1L);
    }

    private String between(long lowerSortKey, long upperSortKey) {
        if (lowerSortKey >= upperSortKey) {
            throw new IllegalArgumentException("형제 문서 sortKey 순서가 올바르지 않습니다.");
        }

        long candidateSortKey = lowerSortKey + ((upperSortKey - lowerSortKey) / 2L);
        if (candidateSortKey <= lowerSortKey || candidateSortKey >= upperSortKey) {
            throw new SortKeyRebalanceRequiredException();
        }

        return format(candidateSortKey);
    }

    private String format(long sortKey) {
        return String.format(Locale.ROOT, "%0" + SORT_KEY_WIDTH + "d", sortKey);
    }

    public static final class SortKeyRebalanceRequiredException extends RuntimeException {
    }
}
