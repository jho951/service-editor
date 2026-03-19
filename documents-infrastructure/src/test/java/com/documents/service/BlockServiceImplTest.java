package com.documents.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.documents.domain.Block;
import com.documents.domain.BlockType;
import com.documents.domain.Document;
import com.documents.domain.Workspace;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.BlockRepository;
import com.documents.repository.DocumentRepository;
import com.documents.support.OrderedSortKeyGenerator;
import com.documents.support.TextNormalizer;

@ExtendWith(MockitoExtension.class)
@DisplayName("Block 서비스 구현 검증")
class BlockServiceImplTest {

    private static final String ACTOR_ID = "user-123";
    private static final String SIMPLE_CONTENT = "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"새 블록\",\"marks\":[]}]}";
    private static final String UPDATED_CONTENT = "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"수정된 블록\",\"marks\":[]}]}";

    @Mock
    private BlockRepository blockRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private TextNormalizer textNormalizer;

    private BlockServiceImpl blockService;

    @BeforeEach
    void setUp() {
        blockService = new BlockServiceImpl(
                blockRepository,
                documentRepository,
                textNormalizer,
                new OrderedSortKeyGenerator()
        );
    }

    @Test
    @DisplayName("성공_문서 블록 목록 조회 시 활성 블록 전체를 반환한다")
    void getAllByDocumentIdReturnsActiveBlocks() {
        UUID documentId = UUID.randomUUID();
        Block rootBlock = block(documentId, null, "000000000001000000000000");
        Block childBlock = block(documentId, rootBlock.getId(), "000000000001I00000000000");

        when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document(documentId)));
        when(blockRepository.findActiveByDocumentIdOrderBySortKey(documentId))
                .thenReturn(List.of(rootBlock, childBlock));

        assertThat(blockService.getAllByDocumentId(documentId))
                .containsExactly(rootBlock, childBlock);
    }

    @Test
    @DisplayName("성공_루트 텍스트 블록 생성 시 gap 기반 sortKey 전략으로 저장한다")
    void createRootBlockAppendsToDocumentRoot() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document(documentId)));
        when(blockRepository.countActiveByDocumentId(documentId)).thenReturn(0L);
        when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);
        when(blockRepository.findActiveByDocumentIdAndParentIdOrderBySortKey(documentId, null))
                .thenReturn(new ArrayList<>());
        when(blockRepository.save(any(Block.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Block created = blockService.create(documentId, null, BlockType.TEXT, SIMPLE_CONTENT, null, null, ACTOR_ID);

        ArgumentCaptor<Block> captor = ArgumentCaptor.forClass(Block.class);
        verify(blockRepository).save(captor.capture());
        Block saved = captor.getValue();

        assertThat(created).isSameAs(saved);
        assertThat(saved.getDocumentId()).isEqualTo(documentId);
        assertThat(saved.getParentId()).isNull();
        assertThat(saved.getType()).isEqualTo(BlockType.TEXT);
        assertThat(saved.getContent()).isEqualTo(SIMPLE_CONTENT);
        assertThat(saved.getSortKey()).isEqualTo("000000000001000000000000");
        assertThat(saved.getCreatedBy()).isEqualTo(ACTOR_ID);
        assertThat(saved.getUpdatedBy()).isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("성공_afterBlockId가 있으면 같은 부모의 다음 위치용 gap sortKey로 블록을 저장한다")
    void createBlockInsertsAfterAnchor() {
        UUID documentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Block first = block(documentId, parentId, "000000000001000000000000");
        Block second = block(documentId, parentId, "000000000002000000000000");

        when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document(documentId)));
        when(blockRepository.countActiveByDocumentId(documentId)).thenReturn(2L);
        when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);
        when(blockRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(
                block(documentId, null, "000000000001000000000000")
        ));
        when(blockRepository.findActiveByDocumentIdAndParentIdOrderBySortKey(documentId, parentId))
                .thenReturn(new ArrayList<>(List.of(first, second)));
        when(blockRepository.save(any(Block.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Block created = blockService.create(
                documentId,
                parentId,
                BlockType.TEXT,
                "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"중간 블록\",\"marks\":[]}]}",
                first.getId(),
                null,
                ACTOR_ID
        );

        assertThat(created.getSortKey()).isEqualTo("000000000001I00000000000");
        assertThat(second.getSortKey()).isEqualTo("000000000002000000000000");
    }

    @Test
    @DisplayName("실패_부모 블록이 다른 문서에 있으면 잘못된 요청 예외를 던진다")
    void createBlockThrowsWhenParentBelongsToOtherDocument() {
        UUID documentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document(documentId)));
        when(blockRepository.countActiveByDocumentId(documentId)).thenReturn(0L);
        when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);
        when(blockRepository.findByIdAndDeletedAtIsNull(parentId))
                .thenReturn(Optional.of(block(
                        UUID.randomUUID(),
                        null,
                        "000000000001000000000000"
                )));

        assertThatThrownBy(() -> blockService.create(documentId, parentId, BlockType.TEXT, SIMPLE_CONTENT, null, null, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("잘못된 요청입니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_afterBlockId가 같은 부모 형제가 아니면 잘못된 요청 예외를 던진다")
    void createBlockThrowsWhenAfterBlockIdIsNotSibling() {
        UUID documentId = UUID.randomUUID();

        when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document(documentId)));
        when(blockRepository.countActiveByDocumentId(documentId)).thenReturn(0L);
        when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);
        when(blockRepository.findActiveByDocumentIdAndParentIdOrderBySortKey(documentId, null))
                .thenReturn(new ArrayList<>());

        assertThatThrownBy(() -> blockService.create(
                documentId,
                null,
                BlockType.TEXT,
                SIMPLE_CONTENT,
                UUID.randomUUID(),
                null,
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("잘못된 요청입니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_부모 블록이 없으면 블록 없음 예외를 던진다")
    void createBlockThrowsWhenParentMissing() {
        UUID documentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document(documentId)));
        when(blockRepository.countActiveByDocumentId(documentId)).thenReturn(0L);
        when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);
        when(blockRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> blockService.create(documentId, parentId, BlockType.TEXT, SIMPLE_CONTENT, null, null, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("요청한 블록을 찾을 수 없습니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.BLOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("성공_블록 수정 시 text와 updatedBy를 갱신한다")
    void updateBlockUpdatesTextAndActor() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block block = block(documentId, null, "000000000001000000000000");
        block.setId(blockId);
        block.setUpdatedBy("old-user");

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));
        when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);

        block.setVersion(0);

        Block updated = blockService.update(blockId, UPDATED_CONTENT, 0, ACTOR_ID);

        assertThat(updated).isSameAs(block);
        assertThat(block.getContent()).isEqualTo(UPDATED_CONTENT);
        assertThat(block.getUpdatedBy()).isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("실패_수정 대상 블록이 없으면 블록 없음 예외를 던진다")
    void updateBlockThrowsWhenBlockMissing() {
        UUID blockId = UUID.randomUUID();

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> blockService.update(blockId, UPDATED_CONTENT, 0, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("요청한 블록을 찾을 수 없습니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.BLOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("실패_수정자 식별자가 비어 있으면 잘못된 요청 예외를 던진다")
    void updateBlockThrowsWhenActorInvalid() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block block = block(documentId, null, "000000000001000000000000");
        block.setId(blockId);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));
        when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(null);

        block.setVersion(0);

        assertThatThrownBy(() -> blockService.update(blockId, UPDATED_CONTENT, 0, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("잘못된 요청입니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_요청 version이 현재 블록 version과 다르면 충돌 예외를 던진다")
    void updateBlockThrowsWhenVersionMismatched() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block block = block(documentId, null, "000000000001000000000000");
        block.setId(blockId);
        block.setVersion(1);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));
        when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);

        assertThatThrownBy(() -> blockService.update(blockId, UPDATED_CONTENT, 0, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("요청이 현재 리소스 상태와 충돌합니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("성공_블록 삭제 시 활성 하위 블록까지 같은 시각으로 bulk soft delete 처리한다")
    void deleteSoftDeletesDescendantBlocks() {
        UUID documentId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID grandChildId = UUID.randomUUID();
        Block rootBlock = block(documentId, null, "000000000001000000000000");
        rootBlock.setId(rootId);
        Block childBlock = block(documentId, rootId, "000000000001I00000000000");
        childBlock.setId(childId);
        Block grandChildBlock = block(documentId, childId, "000000000001Q00000000000");
        grandChildBlock.setId(grandChildId);

        when(blockRepository.findByIdAndDeletedAtIsNull(rootId)).thenReturn(Optional.of(rootBlock));
        when(blockRepository.findActiveChildrenByParentIdOrderBySortKey(rootId))
                .thenReturn(List.of(childBlock));
        when(blockRepository.findActiveChildrenByParentIdOrderBySortKey(childId))
                .thenReturn(List.of(grandChildBlock));
        when(blockRepository.findActiveChildrenByParentIdOrderBySortKey(grandChildId))
                .thenReturn(List.of());
        when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);

        blockService.delete(rootId, ACTOR_ID);

        ArgumentCaptor<LocalDateTime> deletedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(blockRepository).softDeleteActiveByIds(
                eq(List.of(rootId, childId, grandChildId)),
                eq(ACTOR_ID),
                deletedAtCaptor.capture()
        );
    }

    @Test
    @DisplayName("실패_이미 삭제되었거나 없는 블록은 삭제할 수 없다")
    void deleteThrowsWhenBlockMissing() {
        UUID blockId = UUID.randomUUID();
        when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);
        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> blockService.delete(blockId, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("요청한 블록을 찾을 수 없습니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.BLOCK_NOT_FOUND);
    }

    private Document document(UUID documentId) {
        return Document.builder()
                .id(documentId)
                .workspace(Workspace.builder()
                        .id(UUID.randomUUID())
                        .name("Docs Root")
                        .build())
                .title("문서")
                .sortKey("00000000000000000001")
                .build();
    }

    private Block block(UUID documentId, UUID parentId, String sortKey) {
        return Block.builder()
                .id(UUID.randomUUID())
                .document(document(documentId))
                .parent(parentId == null ? null : parentBlock(parentId, documentId))
                .type(BlockType.TEXT)
                .content(toContent("기존 블록"))
                .sortKey(sortKey)
                .build();
    }

    private Block parentBlock(UUID blockId, UUID documentId) {
        return Block.builder()
                .id(blockId)
                .document(document(documentId))
                .type(BlockType.TEXT)
                .content(toContent("부모 블록"))
                .sortKey("000000000001000000000000")
                .build();
    }

    private String toContent(String text) {
        return "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"%s\",\"marks\":[]}]}".formatted(text);
    }

}
