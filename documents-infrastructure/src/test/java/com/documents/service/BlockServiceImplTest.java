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
    @DisplayName("성공_수정 내용이 기존과 같으면 no-op으로 성공 처리하고 변경하지 않는다")
    void updateDoesNothingWhenContentIsSame() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block block = block(documentId, null, "000000000001000000000000");
        block.setId(blockId);
        block.setVersion(0);
        block.setUpdatedBy("old-user");

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));

        Block updated = blockService.update(blockId, toContent("기존 블록"), 0, ACTOR_ID);

        assertThat(updated).isSameAs(block);
        assertThat(block.getContent()).isEqualTo(toContent("기존 블록"));
        assertThat(block.getUpdatedBy()).isEqualTo("old-user");
        verify(textNormalizer, never()).normalizeNullable(ACTOR_ID);
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
        assertThatThrownBy(() -> blockService.update(blockId, UPDATED_CONTENT, 0, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("요청이 현재 리소스 상태와 충돌합니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("성공_parentId와 anchor가 모두 null이면 루트 형제 집합의 마지막 위치로 이동한다")
    void moveMovesBlockToDocumentRootLastPosition() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block block = block(documentId, UUID.randomUUID(), "000000000001000000000000");
        block.setId(blockId);
        block.setVersion(1);
        block.setUpdatedBy("old-user");
        Block sibling = block(documentId, null, "000000000002000000000000");

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));
        when(blockRepository.findActiveByDocumentIdAndParentIdOrderBySortKey(documentId, null))
                .thenReturn(List.of(sibling));
        when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);

        blockService.move(blockId, null, null, null, 1, ACTOR_ID);

        assertThat(block.getParentId()).isNull();
        assertThat(block.getSortKey()).isEqualTo("000000000003000000000000");
        assertThat(block.getUpdatedBy()).isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("성공_같은 부모에서 afterBlockId 기준으로 재정렬하면 새 sortKey와 updatedBy를 반영한다")
    void moveReordersWithinSameParent() {
        UUID documentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID afterBlockId = UUID.randomUUID();
        Block block = block(documentId, parentId, "000000000003000000000000");
        block.setId(blockId);
        block.setVersion(1);
        block.setUpdatedBy("old-user");
        Block parent = parentBlock(parentId, documentId);
        Block afterBlock = block(documentId, parentId, "000000000001000000000000");
        afterBlock.setId(afterBlockId);
        Block nextBlock = block(documentId, parentId, "000000000002000000000000");

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));
        when(blockRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));
        when(blockRepository.findActiveByDocumentIdAndParentIdOrderBySortKey(documentId, parentId))
                .thenReturn(List.of(afterBlock, nextBlock, block));
        when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);

        blockService.move(blockId, parentId, afterBlockId, null, 1, ACTOR_ID);

        assertThat(block.getParentId()).isEqualTo(parentId);
        assertThat(block.getSortKey()).isEqualTo("000000000001I00000000000");
        assertThat(block.getUpdatedBy()).isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("성공_다른 부모로 이동하면 parentId와 sortKey와 updatedBy를 함께 갱신한다")
    void moveUpdatesParentSortKeyAndUpdatedBy() {
        UUID documentId = UUID.randomUUID();
        UUID currentParentId = UUID.randomUUID();
        UUID targetParentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block block = block(documentId, currentParentId, "000000000003000000000000");
        block.setId(blockId);
        block.setVersion(1);
        block.setUpdatedBy("old-user");
        Block targetParent = parentBlock(targetParentId, documentId);
        Block sibling = block(documentId, targetParentId, "000000000001000000000000");

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));
        when(blockRepository.findByIdAndDeletedAtIsNull(targetParentId)).thenReturn(Optional.of(targetParent));
        when(blockRepository.findActiveByDocumentIdAndParentIdOrderBySortKey(documentId, targetParentId))
                .thenReturn(List.of(sibling));
        when(textNormalizer.normalizeNullable(" user-456 ")).thenReturn("user-456");

        blockService.move(blockId, targetParentId, null, null, 1, " user-456 ");

        assertThat(block.getParentId()).isEqualTo(targetParentId);
        assertThat(block.getSortKey()).isEqualTo("000000000002000000000000");
        assertThat(block.getUpdatedBy()).isEqualTo("user-456");
    }

    @Test
    @DisplayName("성공_요청 위치가 현재 위치와 같으면 no-op으로 성공 처리하고 변경하지 않는다")
    void moveDoesNothingWhenTargetLocationIsSame() {
        UUID documentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block block = block(documentId, parentId, "000000000001000000000000");
        block.setId(blockId);
        block.setVersion(1);
        block.setUpdatedBy("old-user");
        Block parent = parentBlock(parentId, documentId);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));
        when(blockRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));
        when(blockRepository.findActiveByDocumentIdAndParentIdOrderBySortKey(documentId, parentId))
                .thenReturn(List.of(block));

        blockService.move(blockId, parentId, null, null, 1, ACTOR_ID);

        assertThat(block.getParentId()).isEqualTo(parentId);
        assertThat(block.getSortKey()).isEqualTo("000000000001000000000000");
        assertThat(block.getUpdatedBy()).isEqualTo("old-user");
        verify(textNormalizer, never()).normalizeNullable(ACTOR_ID);
    }

    @Test
    @DisplayName("실패_이동 대상 블록이 없으면 블록 없음 예외를 던진다")
    void moveThrowsWhenBlockMissing() {
        UUID blockId = UUID.randomUUID();

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> blockService.move(blockId, null, null, null, 0, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("요청한 블록을 찾을 수 없습니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.BLOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("실패_version이 현재 블록 version과 다르면 충돌 예외를 던진다")
    void moveThrowsWhenVersionMismatched() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block block = block(documentId, null, "000000000001000000000000");
        block.setId(blockId);
        block.setVersion(1);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));

        assertThatThrownBy(() -> blockService.move(blockId, null, null, null, 0, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("요청이 현재 리소스 상태와 충돌합니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("실패_parentId가 존재하지 않으면 블록 없음 예외를 던진다")
    void moveThrowsWhenParentMissing() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Block block = block(documentId, null, "000000000001000000000000");
        block.setId(blockId);
        block.setVersion(1);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));
        when(blockRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> blockService.move(blockId, parentId, null, null, 1, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("요청한 블록을 찾을 수 없습니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.BLOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("실패_대상 부모가 다른 문서 소속 블록이면 잘못된 요청 예외를 던진다")
    void moveThrowsWhenParentBelongsToOtherDocument() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Block block = block(documentId, null, "000000000001000000000000");
        block.setId(blockId);
        block.setVersion(1);
        Block otherDocumentParent = parentBlock(parentId, UUID.randomUUID());

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));
        when(blockRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(otherDocumentParent));

        assertThatThrownBy(() -> blockService.move(blockId, parentId, null, null, 1, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("잘못된 요청입니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_자기 자신을 부모로 지정하면 잘못된 요청 예외를 던진다")
    void moveThrowsWhenParentIsSelf() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block block = block(documentId, null, "000000000001000000000000");
        block.setId(blockId);
        block.setVersion(1);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));

        assertThatThrownBy(() -> blockService.move(blockId, blockId, null, null, 1, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_자기 하위 블록을 부모로 지정하면 순환 이동 예외를 던진다")
    void moveThrowsWhenCycleDetected() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        Block block = block(documentId, null, "000000000001000000000000");
        block.setId(blockId);
        block.setVersion(1);
        Block child = block(documentId, blockId, "000000000002000000000000");
        child.setId(childId);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));
        when(blockRepository.findByIdAndDeletedAtIsNull(childId)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> blockService.move(blockId, childId, null, null, 1, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_이동 대상 부모 깊이가 최대 깊이를 넘기면 잘못된 요청 예외를 던진다")
    void moveThrowsWhenTargetParentDepthExceedsLimit() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID parent1Id = UUID.randomUUID();
        UUID parent2Id = UUID.randomUUID();
        UUID parent3Id = UUID.randomUUID();
        UUID parent4Id = UUID.randomUUID();
        UUID parent5Id = UUID.randomUUID();
        UUID parent6Id = UUID.randomUUID();
        UUID parent7Id = UUID.randomUUID();
        UUID parent8Id = UUID.randomUUID();
        UUID parent9Id = UUID.randomUUID();
        UUID parent10Id = UUID.randomUUID();

        Block block = block(documentId, null, "000000000001000000000000");
        block.setId(blockId);
        block.setVersion(1);

        Block parent1 = parentBlock(parent1Id, documentId);
        Block parent2 = parentBlock(parent2Id, documentId);
        parent2.setParent(parent1);
        Block parent3 = parentBlock(parent3Id, documentId);
        parent3.setParent(parent2);
        Block parent4 = parentBlock(parent4Id, documentId);
        parent4.setParent(parent3);
        Block parent5 = parentBlock(parent5Id, documentId);
        parent5.setParent(parent4);
        Block parent6 = parentBlock(parent6Id, documentId);
        parent6.setParent(parent5);
        Block parent7 = parentBlock(parent7Id, documentId);
        parent7.setParent(parent6);
        Block parent8 = parentBlock(parent8Id, documentId);
        parent8.setParent(parent7);
        Block parent9 = parentBlock(parent9Id, documentId);
        parent9.setParent(parent8);
        Block parent10 = parentBlock(parent10Id, documentId);
        parent10.setParent(parent9);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));
        when(blockRepository.findByIdAndDeletedAtIsNull(parent10Id)).thenReturn(Optional.of(parent10));
        when(blockRepository.findByIdAndDeletedAtIsNull(parent9Id)).thenReturn(Optional.of(parent9));
        when(blockRepository.findByIdAndDeletedAtIsNull(parent8Id)).thenReturn(Optional.of(parent8));
        when(blockRepository.findByIdAndDeletedAtIsNull(parent7Id)).thenReturn(Optional.of(parent7));
        when(blockRepository.findByIdAndDeletedAtIsNull(parent6Id)).thenReturn(Optional.of(parent6));
        when(blockRepository.findByIdAndDeletedAtIsNull(parent5Id)).thenReturn(Optional.of(parent5));
        when(blockRepository.findByIdAndDeletedAtIsNull(parent4Id)).thenReturn(Optional.of(parent4));
        when(blockRepository.findByIdAndDeletedAtIsNull(parent3Id)).thenReturn(Optional.of(parent3));
        when(blockRepository.findByIdAndDeletedAtIsNull(parent2Id)).thenReturn(Optional.of(parent2));
        when(blockRepository.findByIdAndDeletedAtIsNull(parent1Id)).thenReturn(Optional.of(parent1));

        assertThatThrownBy(() -> blockService.move(blockId, parent10Id, null, null, 1, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("잘못된 요청입니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);

        verify(blockRepository, never()).findActiveByDocumentIdAndParentIdOrderBySortKey(any(), any());
    }

    @Test
    @DisplayName("실패_afterBlockId가 대상 부모의 형제가 아니면 잘못된 요청 예외를 던진다")
    void moveThrowsWhenAfterBlockIsNotSibling() {
        UUID documentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block block = block(documentId, null, "000000000003000000000000");
        block.setId(blockId);
        block.setVersion(1);
        Block parent = parentBlock(parentId, documentId);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));
        when(blockRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));
        when(blockRepository.findActiveByDocumentIdAndParentIdOrderBySortKey(documentId, parentId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> blockService.move(blockId, parentId, UUID.randomUUID(), null, 1, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("잘못된 요청입니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_afterBlockId와 beforeBlockId가 같은 삽입 간격을 가리키지 않으면 잘못된 요청 예외를 던진다")
    void moveThrowsWhenAnchorsAreContradictory() {
        UUID documentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID afterBlockId = UUID.randomUUID();
        UUID beforeBlockId = UUID.randomUUID();
        Block block = block(documentId, null, "000000000009000000000000");
        block.setId(blockId);
        block.setVersion(1);
        Block parent = parentBlock(parentId, documentId);
        Block afterBlock = block(documentId, parentId, "000000000001000000000000");
        afterBlock.setId(afterBlockId);
        Block middleBlock = block(documentId, parentId, "000000000002000000000000");
        Block beforeBlock = block(documentId, parentId, "000000000003000000000000");
        beforeBlock.setId(beforeBlockId);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));
        when(blockRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));
        when(blockRepository.findActiveByDocumentIdAndParentIdOrderBySortKey(documentId, parentId))
                .thenReturn(List.of(afterBlock, middleBlock, beforeBlock));

        assertThatThrownBy(() -> blockService.move(blockId, parentId, afterBlockId, beforeBlockId, 1, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("잘못된 요청입니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_sort key gap이 부족하면 재정렬 필요 예외를 던진다")
    void moveThrowsWhenSortKeySpaceExhausted() {
        UUID documentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID afterBlockId = UUID.randomUUID();
        UUID beforeBlockId = UUID.randomUUID();
        Block block = block(documentId, null, "000000000009000000000000");
        block.setId(blockId);
        block.setVersion(1);
        Block parent = parentBlock(parentId, documentId);
        Block afterBlock = block(documentId, parentId, "000000000001000000000000");
        afterBlock.setId(afterBlockId);
        Block beforeBlock = block(documentId, parentId, "000000000001000000000001");
        beforeBlock.setId(beforeBlockId);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));
        when(blockRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));
        when(blockRepository.findActiveByDocumentIdAndParentIdOrderBySortKey(documentId, parentId))
                .thenReturn(List.of(afterBlock, beforeBlock));

        assertThatThrownBy(() -> blockService.move(blockId, parentId, afterBlockId, beforeBlockId, 1, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("정렬 키 공간이 부족하여 재정렬이 필요합니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.SORT_KEY_REBALANCE_REQUIRED);
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

        rootBlock.setVersion(0);

        when(blockRepository.softDeleteActiveByIdsWithRootVersion(
                eq(List.of(rootId, childId, grandChildId)),
                eq(rootId),
                eq(0),
                eq(ACTOR_ID),
                any(LocalDateTime.class)
        )).thenReturn(3);

        Block result = blockService.delete(rootId, 0, ACTOR_ID);

        ArgumentCaptor<LocalDateTime> deletedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(blockRepository).softDeleteActiveByIdsWithRootVersion(
                eq(List.of(rootId, childId, grandChildId)),
                eq(rootId),
                eq(0),
                eq(ACTOR_ID),
                deletedAtCaptor.capture()
        );
        assertThat(result.getId()).isEqualTo(rootId);
        assertThat(result.getDeletedAt()).isEqualTo(deletedAtCaptor.getValue());
        assertThat(result.getUpdatedBy()).isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("실패_이미 삭제되었거나 없는 블록은 삭제할 수 없다")
    void deleteThrowsWhenBlockMissing() {
        UUID blockId = UUID.randomUUID();
        when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);
        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> blockService.delete(blockId, 0, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("요청한 블록을 찾을 수 없습니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.BLOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("실패_삭제 직전 root version이 바뀌면 충돌 예외를 던진다")
    void deleteThrowsWhenRootVersionChangesBeforeSoftDelete() {
        UUID documentId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        Block rootBlock = block(documentId, null, "000000000001000000000000");
        rootBlock.setId(rootId);
        rootBlock.setVersion(0);

        when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);
        when(blockRepository.findByIdAndDeletedAtIsNull(rootId)).thenReturn(Optional.of(rootBlock));
        when(blockRepository.findActiveChildrenByParentIdOrderBySortKey(rootId)).thenReturn(List.of());
        when(blockRepository.softDeleteActiveByIdsWithRootVersion(
                eq(List.of(rootId)),
                eq(rootId),
                eq(0),
                eq(ACTOR_ID),
                any(LocalDateTime.class)
        )).thenReturn(0);

        assertThatThrownBy(() -> blockService.delete(rootId, 0, ACTOR_ID))
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
