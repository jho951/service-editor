package com.documents.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.documents.repository.DocumentRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("문서 sortKey 생성 유틸 검증")
class DocumentSortKeyGeneratorTest {

    @Mock
    private DocumentRepository documentRepository;

    @Test
    @DisplayName("성공_기존 형제가 없으면 첫 sortKey를 발급한다")
    void genNextSortKeySortKeyStartsFromOne() {
        UUID workspaceId = UUID.randomUUID();
        DocumentSortKeyGenerator generator = new DocumentSortKeyGenerator(documentRepository);
        when(documentRepository.findMaxSortKeyByWorkspaceIdAndParentId(workspaceId, null)).thenReturn(Optional.empty());

        assertThat(generator.genNextSortKey(workspaceId, null)).isEqualTo("00000000000000000001");
    }

    @Test
    @DisplayName("성공_기존 최대 sortKey가 있으면 다음 순번을 발급한다")
    void genNextSortKeySortKeyIncrementsCurrentMax() {
        UUID workspaceId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        DocumentSortKeyGenerator generator = new DocumentSortKeyGenerator(documentRepository);
        when(documentRepository.findMaxSortKeyByWorkspaceIdAndParentId(workspaceId, parentId))
                .thenReturn(Optional.of("00000000000000000009"));

        assertThat(generator.genNextSortKey(workspaceId, parentId)).isEqualTo("00000000000000000010");
    }

    @Test
    @DisplayName("실패_sortKey 형식이 숫자가 아니면 예외를 던진다")
    void genNextSortKeySortKeyThrowsWhenStoredSortKeyIsInvalid() {
        UUID workspaceId = UUID.randomUUID();
        DocumentSortKeyGenerator generator = new DocumentSortKeyGenerator(documentRepository);
        when(documentRepository.findMaxSortKeyByWorkspaceIdAndParentId(workspaceId, null)).thenReturn(Optional.of("rank-a"));

        assertThatThrownBy(() -> generator.genNextSortKey(workspaceId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Document sortKey must be a numeric string.");
    }
}
