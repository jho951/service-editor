package com.documents.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Block 서비스 구현 검증")
class BlockServiceImplTest {

    private static final String ACTOR_ID = "user-123";

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
                .thenReturn(java.util.List.of(rootBlock, childBlock));

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

        Block created = blockService.create(documentId, null, BlockType.TEXT, "새 블록", null, null, ACTOR_ID);

        ArgumentCaptor<Block> captor = ArgumentCaptor.forClass(Block.class);
        verify(blockRepository).save(captor.capture());
        Block saved = captor.getValue();

        assertThat(created).isSameAs(saved);
        assertThat(saved.getDocumentId()).isEqualTo(documentId);
        assertThat(saved.getParentId()).isNull();
        assertThat(saved.getType()).isEqualTo(BlockType.TEXT);
        assertThat(saved.getText()).isEqualTo("새 블록");
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
                .thenReturn(new ArrayList<>(java.util.List.of(first, second)));
        when(blockRepository.save(any(Block.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Block created = blockService.create(documentId, parentId, BlockType.TEXT, "중간 블록", first.getId(), null, ACTOR_ID);

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

        assertThatThrownBy(() -> blockService.create(documentId, parentId, BlockType.TEXT, "새 블록", null, null, ACTOR_ID))
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
                "새 블록",
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

        assertThatThrownBy(() -> blockService.create(documentId, parentId, BlockType.TEXT, "새 블록", null, null, ACTOR_ID))
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

        Block updated = blockService.update(blockId, "수정된 블록", 0, ACTOR_ID);

        assertThat(updated).isSameAs(block);
        assertThat(block.getText()).isEqualTo("수정된 블록");
        assertThat(block.getUpdatedBy()).isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("실패_수정 대상 블록이 없으면 블록 없음 예외를 던진다")
    void updateBlockThrowsWhenBlockMissing() {
        UUID blockId = UUID.randomUUID();

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> blockService.update(blockId, "수정된 블록", 0, ACTOR_ID))
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

        assertThatThrownBy(() -> blockService.update(blockId, "수정된 블록", 0, ACTOR_ID))
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

        assertThatThrownBy(() -> blockService.update(blockId, "수정된 블록", 0, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("요청이 현재 리소스 상태와 충돌합니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.CONFLICT);
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
                .text("기존 블록")
                .sortKey(sortKey)
                .build();
    }

    private Block parentBlock(UUID blockId, UUID documentId) {
        return Block.builder()
                .id(blockId)
                .document(document(documentId))
                .type(BlockType.TEXT)
                .text("부모 블록")
                .sortKey("000000000001000000000000")
                .build();
    }

}
