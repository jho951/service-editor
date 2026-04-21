package com.documents.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.documents.domain.Block;
import com.documents.domain.BlockType;
import com.documents.domain.Document;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.BlockRepository;
import com.documents.repository.DocumentRepository;
import com.documents.service.editor.EditorSaveAppliedOperationResult;
import com.documents.service.editor.EditorSaveCommand;
import com.documents.service.editor.EditorMoveCommand;
import com.documents.service.editor.EditorMoveResourceType;
import com.documents.service.editor.EditorMoveResult;
import com.documents.service.editor.EditorSaveOperationCommand;
import com.documents.service.editor.EditorSaveOperationStatus;
import com.documents.service.editor.EditorSaveOperationType;
import com.documents.service.editor.EditorSaveResult;
import com.documents.support.OrderedSortKeyGenerator;
import com.documents.support.TextNormalizer;
import com.documents.service.transaction.DocumentVersionUpdater;
import com.documents.service.transaction.PersistenceContextManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("EditorOperation 오케스트레이터 save 구현 검증")
class EditorOperationOrchestratorImplTest {

    private static final String ACTOR_ID = "user-123";
    private static final String EMPTY_BLOCK_CONTENT =
            "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"\",\"marks\":[]}]}";
    private static final String REPLACED_CONTENT =
            "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"새 블록\",\"marks\":[]}]}";

    @Mock
    private BlockService blockService;

    @Mock
    private BlockRepository blockRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentService documentService;

    @Mock
    private DocumentVersionUpdater documentVersionUpdater;

    @Mock
    private PersistenceContextManager persistenceContextManager;

    @Mock
    private DocumentResourceBindingService documentResourceBindingService;

    @Mock
    private DocumentAccessGuard documentAccessGuard;

    @Mock
    private BlockAccessGuard blockAccessGuard;

    private EditorOperationOrchestratorImpl editorOperationOrchestrator;

    @BeforeEach
    void setUp() {
        BlockService editorBlockService = new BlockServiceImpl(
                blockRepository,
                documentRepository,
                documentVersionUpdater,
                new TextNormalizer(),
                new OrderedSortKeyGenerator(),
                documentResourceBindingService
        ) {
            @Override
            public Block create(
                    Document document,
                    UUID parentId,
                    BlockType type,
                    String content,
                    UUID afterBlockId,
                    UUID beforeBlockId,
                    String actorId
            ) {
                return blockService.create(document, parentId, type, content, afterBlockId, beforeBlockId, actorId);
            }

            @Override
            public Block update(UUID blockId, String content, Integer version, String actorId) {
                return blockService.update(blockId, content, version, actorId);
            }

            @Override
            public Block move(
                    UUID blockId,
                    UUID parentId,
                    UUID afterBlockId,
                    UUID beforeBlockId,
                    Integer version,
                    String actorId
            ) {
                return blockService.move(blockId, parentId, afterBlockId, beforeBlockId, version, actorId);
            }

            @Override
            public Block delete(UUID blockId, Integer version, String actorId) {
                return blockService.delete(blockId, version, actorId);
            }

        };

        editorOperationOrchestrator = new EditorOperationOrchestratorImpl(
                documentService,
                editorBlockService,
                new EditorSaveOperationExecutor(editorBlockService, persistenceContextManager),
                new EditorMoveResultMapper(),
                documentVersionUpdater,
                persistenceContextManager,
                documentAccessGuard,
                blockAccessGuard
        );
        lenient().when(documentService.getById(any(UUID.class)))
                .thenAnswer(invocation -> document(invocation.getArgument(0), 0));
        lenient().when(documentAccessGuard.requireReadable(any(UUID.class), anyString()))
                .thenAnswer(invocation -> document(invocation.getArgument(0), 0));
        lenient().when(documentAccessGuard.requireWritable(any(UUID.class), anyString()))
                .thenAnswer(invocation -> document(invocation.getArgument(0), 0));
        lenient().when(blockAccessGuard.requireWritable(any(UUID.class), anyString()))
                .thenAnswer(invocation -> block(invocation.getArgument(0), UUID.randomUUID(), "000000000001000000000000", 0));
        lenient().when(documentVersionUpdater.increment(any(UUID.class), anyString(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> document(invocation.getArgument(0), 1));
    }

    @Test
    @DisplayName("성공_create 후 tempId replace_content를 같은 save batch에서 반영한다")
    void applyCreatesBlockAndReplacesContent() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        Block createdBlock = block(blockId, documentId, "000000000001000000000000", 0);
        Block updatedBlock = block(blockId, documentId, "000000000001000000000000", 1);
        updatedBlock.setContent(REPLACED_CONTENT);

        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdBlock);
        when(documentService.getById(documentId)).thenReturn(document(documentId, 0));
        when(blockService.update(blockId, REPLACED_CONTENT, 0, ACTOR_ID)).thenReturn(updatedBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        "tmp:block:1",
                                        null,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.batchId()).isEqualTo("batch-1");
        assertThat(result.appliedOperations()).extracting(EditorSaveAppliedOperationResult::opId)
                .containsExactly("op-1", "op-2");
        assertThat(result.documentVersion()).isEqualTo(1);
        assertThat(result.appliedOperations().get(0).tempId()).isEqualTo("tmp:block:1");
        assertThat(result.appliedOperations().get(0).blockId()).isEqualTo(blockId);
        assertThat(result.appliedOperations().get(1).blockId()).isEqualTo(blockId);
        assertThat(result.appliedOperations().get(1).version()).isEqualTo(1);

        verify(blockService).update(blockId, REPLACED_CONTENT, 0, ACTOR_ID);
        verify(documentService).getById(documentId);
        verify(persistenceContextManager, atLeastOnce()).flush();
    }

    @Test
    @DisplayName("성공_create에 content가 오면 초기 본문으로 저장한다")
    void applyCreatesBlockWithInitialContent() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block createdBlock = block(blockId, documentId, "000000000001000000000000", 0);
        createdBlock.setContent(REPLACED_CONTENT);

        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(REPLACED_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdBlock);
        when(documentService.getById(documentId)).thenReturn(document(documentId, 0));

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-create-with-content",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:block:1",
                                        null,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).hasSize(1);
        assertThat(result.appliedOperations().get(0).blockId()).isEqualTo(blockId);
        assertThat(result.appliedOperations().get(0).version()).isEqualTo(0);
        assertThat(result.documentVersion()).isEqualTo(1);

        verify(blockService).create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(REPLACED_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        );
        verify(blockService, never()).update(any(UUID.class), anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("성공_block_delete는 version 검증 후 블록 삭제와 삭제 시각 응답을 반환한다")
    void applyDeletesBlock() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        LocalDateTime deletedAt = LocalDateTime.of(2026, 3, 22, 22, 0);

        Block block = block(blockId, documentId, "000000000001000000000000", 4);
        Block deletedBlock = block(blockId, documentId, "000000000001000000000000", 4);
        deletedBlock.setDeletedAt(deletedAt);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));
        when(blockService.delete(blockId, 4, ACTOR_ID)).thenReturn(deletedBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_DELETE,
                                        blockId.toString(),
                                        4L,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).hasSize(1);
        assertThat(result.appliedOperations().get(0).blockId()).isEqualTo(blockId);
        assertThat(result.appliedOperations().get(0).deletedAt()).isEqualTo(deletedAt);

        verify(blockService).delete(blockId, 4, ACTOR_ID);
    }

    @Test
    @DisplayName("성공_existing block move는 전달받은 version과 위치로 move를 호출한다")
    void applyMovesExistingBlockWithRequestedVersion() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID afterBlockId = UUID.randomUUID();
        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 4);
        Block movedBlock = block(blockId, documentId, "000000000001I00000000000", 5);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(existingBlock));
        when(blockService.move(blockId, null, afterBlockId, null, 4, ACTOR_ID)).thenReturn(movedBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        blockId.toString(),
                                        4L,
                                        null,
                                        null,
                                        afterBlockId.toString(),
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).hasSize(1);
        assertThat(result.appliedOperations().get(0).blockId()).isEqualTo(blockId);
        assertThat(result.appliedOperations().get(0).version()).isEqualTo(5);
        assertThat(result.appliedOperations().get(0).sortKey()).isEqualTo("000000000001I00000000000");

        verify(blockService).move(blockId, null, afterBlockId, null, 4, ACTOR_ID);
    }

    @Test
    @DisplayName("성공_move가 no-op이면 NO_OP status와 기존 version을 반환한다")
    void applyReturnsNoOpStatusWhenMoveDoesNotChangeBlockState() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 4);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(existingBlock));
        when(blockService.move(blockId, null, null, null, 4, ACTOR_ID)).thenReturn(existingBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-no-op",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        blockId.toString(),
                                        4L,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).hasSize(1);
        assertThat(result.appliedOperations().get(0).status()).isEqualTo(EditorSaveOperationStatus.NO_OP);
        assertThat(result.appliedOperations().get(0).version()).isEqualTo(4);
        assertThat(result.documentVersion()).isEqualTo(0);

        verify(persistenceContextManager).flush();
    }

    @Test
    @DisplayName("성공_move는 temp parentRef를 실제 parentId로 해석한다")
    void applyResolvesTempParentReferenceForMove() {
        UUID documentId = UUID.randomUUID();
        UUID tempParentId = UUID.randomUUID();
        UUID existingBlockId = UUID.randomUUID();

        Block createdParentBlock = block(tempParentId, documentId, "000000000001000000000000", 0);
        Block existingBlock = block(existingBlockId, documentId, "000000000002000000000000", 1);
        Block movedBlock = block(existingBlockId, documentId, "000000000001I00000000000", 2);

        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdParentBlock);
        when(blockRepository.findByIdAndDeletedAtIsNull(existingBlockId)).thenReturn(Optional.of(existingBlock));
        when(blockService.move(existingBlockId, tempParentId, null, null, 1, ACTOR_ID)).thenReturn(movedBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:parent",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        existingBlockId.toString(),
                                        1L,
                                        null,
                                        "tmp:parent",
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).extracting(EditorSaveAppliedOperationResult::blockId)
                .containsExactly(tempParentId, existingBlockId);
        assertThat(result.appliedOperations().get(1).version()).isEqualTo(2);

        verify(blockService).move(existingBlockId, tempParentId, null, null, 1, ACTOR_ID);
    }

    @Test
    @DisplayName("성공_create 뒤 temp block move 후 replace_content를 적용하면 temp block context version을 갱신한다")
    void applyUpdatesTempBlockVersionAfterMove() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID afterBlockId = UUID.randomUUID();
        String movedContent =
                "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"이동 후 수정\",\"marks\":[]}]}";

        Block createdBlock = block(blockId, documentId, "000000000001000000000000", 0);
        Block movedBlock = block(blockId, documentId, "000000000001I00000000000", 1);
        Block updatedBlock = block(blockId, documentId, "000000000001I00000000000", 2);
        updatedBlock.setContent(movedContent);

        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdBlock);
        when(blockService.move(blockId, null, afterBlockId, null, 0, ACTOR_ID)).thenReturn(movedBlock);
        when(blockService.update(blockId, movedContent, 1, ACTOR_ID)).thenReturn(updatedBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        afterBlockId.toString(),
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-3",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        "tmp:block:1",
                                        null,
                                        movedContent,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).extracting(EditorSaveAppliedOperationResult::version)
                .containsExactly(0L, 1L, 2L);

        verify(blockService).move(blockId, null, afterBlockId, null, 0, ACTOR_ID);
        verify(blockService).update(blockId, movedContent, 1, ACTOR_ID);
    }

    @Test
    @DisplayName("성공_move no-op 뒤 replace_content는 증가하지 않은 version으로 후속 update를 호출한다")
    void applyUsesUnchangedVersionAfterMoveNoOp() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 4);
        Block updatedBlock = block(blockId, documentId, "000000000001000000000000", 5);
        updatedBlock.setContent(REPLACED_CONTENT);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(existingBlock));
        when(blockService.move(blockId, null, null, null, 4, ACTOR_ID)).thenReturn(existingBlock);
        when(blockService.update(blockId, REPLACED_CONTENT, 4, ACTOR_ID)).thenReturn(updatedBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-no-op-then-replace",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        blockId.toString(),
                                        4L,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        blockId.toString(),
                                        4L,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations().get(0).status()).isEqualTo(EditorSaveOperationStatus.NO_OP);
        assertThat(result.appliedOperations().get(1).version()).isEqualTo(5);

        verify(blockService).move(blockId, null, null, null, 4, ACTOR_ID);
        verify(blockService).update(blockId, REPLACED_CONTENT, 4, ACTOR_ID);
    }

    @Test
    @DisplayName("성공_create 뒤 temp block을 두 번 move하면 temp block context version을 연속 갱신한다")
    void applyUpdatesTempBlockVersionAcrossConsecutiveMoves() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID firstParentId = UUID.randomUUID();
        UUID secondParentId = UUID.randomUUID();

        Block createdBlock = block(blockId, documentId, "000000000001000000000000", 0);
        Block firstMovedBlock = block(blockId, documentId, "000000000001I00000000000", 1);
        firstMovedBlock.setParent(Block.builder().id(firstParentId).build());
        Block secondMovedBlock = block(blockId, documentId, "000000000001Q00000000000", 2);
        secondMovedBlock.setParent(Block.builder().id(secondParentId).build());

        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdBlock);
        when(blockService.move(blockId, firstParentId, null, null, 0, ACTOR_ID)).thenReturn(firstMovedBlock);
        when(blockService.move(blockId, secondParentId, null, null, 1, ACTOR_ID)).thenReturn(secondMovedBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-consecutive-moves",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        firstParentId.toString(),
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-3",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        secondParentId.toString(),
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).extracting(EditorSaveAppliedOperationResult::version)
                .containsExactly(0L, 1L, 2L);

        verify(blockService).move(blockId, firstParentId, null, null, 0, ACTOR_ID);
        verify(blockService).move(blockId, secondParentId, null, null, 1, ACTOR_ID);
    }

    @Test
    @DisplayName("실패_replace_content가 알 수 없는 tempId를 참조하면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenReplaceContentReferencesUnknownBlockReference() {
        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                UUID.randomUUID(),
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        "tmp:block:404",
                                        null,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_replace_content가 다른 문서 블록을 참조하면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenReplaceContentReferencesBlockFromOtherDocument() {
        UUID documentId = UUID.randomUUID();
        UUID otherDocumentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block otherDocumentBlock = block(blockId, otherDocumentId, "000000000001000000000000", 0);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(otherDocumentBlock));

        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        blockId.toString(),
                                        0L,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);

        verify(blockService, never()).update(any(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("실패_block_delete가 다른 문서 블록을 참조하면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenDeleteReferencesBlockFromOtherDocument() {
        UUID documentId = UUID.randomUUID();
        UUID otherDocumentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block otherDocumentBlock = block(blockId, otherDocumentId, "000000000001000000000000", 0);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(otherDocumentBlock));

        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_DELETE,
                                        blockId.toString(),
                                        0L,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_block_delete에 낡은 version이 오면 충돌 예외를 던진다")
    void applyThrowsWhenDeleteUsesStaleVersion() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block block = block(blockId, documentId, "000000000001000000000000", 3);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(block));

        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_DELETE,
                                        blockId.toString(),
                                        2L,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("실패_move가 다른 문서 블록을 참조하면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenMoveReferencesBlockFromOtherDocument() {
        UUID documentId = UUID.randomUUID();
        UUID otherDocumentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block otherDocumentBlock = block(blockId, otherDocumentId, "000000000001000000000000", 1);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(otherDocumentBlock));

        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        blockId.toString(),
                                        1L,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);

        verify(blockService, never()).move(any(), any(), any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("실패_move가 존재하지 않는 temp parentRef를 참조하면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenMoveUsesUnknownTempParentReference() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 1);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(existingBlock));

        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        blockId.toString(),
                                        1L,
                                        null,
                                        "tmp:missing-parent",
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_move가 존재하지 않는 temp afterRef를 참조하면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenMoveUsesUnknownTempAfterReference() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 1);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(existingBlock));

        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        blockId.toString(),
                                        1L,
                                        null,
                                        null,
                                        "tmp:missing-after",
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_move가 존재하지 않는 temp beforeRef를 참조하면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenMoveUsesUnknownTempBeforeReference() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 1);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(existingBlock));

        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        blockId.toString(),
                                        1L,
                                        null,
                                        null,
                                        null,
                                        "tmp:missing-before"
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_move가 아직 생성되지 않은 temp anchor를 먼저 참조하면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenMoveReferencesFutureTempAnchor() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 1);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(existingBlock));

        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        blockId.toString(),
                                        1L,
                                        null,
                                        null,
                                        "tmp:future-after",
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:future-after",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_block_delete 뒤 같은 block replace_content를 참조하면 블록 없음 예외를 던진다")
    void applyThrowsWhenReplaceContentReferencesBlockDeletedEarlierInBatch() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block block = block(blockId, documentId, "000000000001000000000000", 2);
        Block deletedBlock = block(blockId, documentId, "000000000001000000000000", 2);
        deletedBlock.setDeletedAt(LocalDateTime.of(2026, 3, 23, 1, 0));

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId))
                .thenReturn(Optional.of(block))
                .thenReturn(Optional.empty());
        when(blockService.delete(blockId, 2, ACTOR_ID)).thenReturn(deletedBlock);

        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-delete-then-replace",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_DELETE,
                                        blockId.toString(),
                                        2L,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        blockId.toString(),
                                        2L,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.BLOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("실패_block_delete 뒤 같은 block move를 참조하면 블록 없음 예외를 던진다")
    void applyThrowsWhenMoveReferencesBlockDeletedEarlierInBatch() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block block = block(blockId, documentId, "000000000001000000000000", 2);
        Block deletedBlock = block(blockId, documentId, "000000000001000000000000", 2);
        deletedBlock.setDeletedAt(LocalDateTime.of(2026, 3, 23, 1, 5));

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId))
                .thenReturn(Optional.of(block))
                .thenReturn(Optional.empty());
        when(blockService.delete(blockId, 2, ACTOR_ID)).thenReturn(deletedBlock);

        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-delete-then-move",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_DELETE,
                                        blockId.toString(),
                                        2L,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        blockId.toString(),
                                        2L,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.BLOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("성공_real block replace_content 뒤 같은 base version move는 서버 내부 최신 version으로 이어서 처리한다")
    void applyUsesServerManagedVersionAfterReplaceContentOnSameRealBlock() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 3);
        Block updatedBlock = block(blockId, documentId, "000000000001000000000000", 4);
        updatedBlock.setContent(REPLACED_CONTENT);
        Block movedBlock = block(blockId, documentId, "000000000001I00000000000", 5);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId))
                .thenReturn(Optional.of(existingBlock))
                .thenReturn(Optional.of(updatedBlock));
        when(blockService.update(blockId, REPLACED_CONTENT, 3, ACTOR_ID)).thenReturn(updatedBlock);
        when(blockService.move(blockId, parentId, null, null, 4, ACTOR_ID)).thenReturn(movedBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-stale-real-block",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        blockId.toString(),
                                        3L,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        blockId.toString(),
                                        3L,
                                        null,
                                        parentId.toString(),
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).extracting(EditorSaveAppliedOperationResult::version)
                .containsExactly(4L, 5L);

        verify(blockService).update(blockId, REPLACED_CONTENT, 3, ACTOR_ID);
        verify(blockService).move(blockId, parentId, null, null, 4, ACTOR_ID);
    }

    @Test
    @DisplayName("성공_real block replace_content, move, replace_content는 같은 base version으로 이어서 처리한다")
    void applyUsesServerManagedVersionAcrossReplaceMoveReplaceOnSameRealBlock() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        String replacedAgainContent =
                "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"다시 수정\",\"marks\":[]}]}";

        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 3);
        Block updatedBlock = block(blockId, documentId, "000000000001000000000000", 4);
        updatedBlock.setContent(REPLACED_CONTENT);
        Block movedBlock = block(blockId, documentId, "000000000001I00000000000", 5);
        movedBlock.setContent(REPLACED_CONTENT);
        Block replacedAgainBlock = block(blockId, documentId, "000000000001I00000000000", 6);
        replacedAgainBlock.setContent(replacedAgainContent);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId))
                .thenReturn(Optional.of(existingBlock))
                .thenReturn(Optional.of(movedBlock));
        when(blockService.update(blockId, REPLACED_CONTENT, 3, ACTOR_ID)).thenReturn(updatedBlock);
        when(blockService.move(blockId, parentId, null, null, 4, ACTOR_ID)).thenReturn(movedBlock);
        when(blockService.update(blockId, replacedAgainContent, 5, ACTOR_ID)).thenReturn(replacedAgainBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-real-block-chain",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        blockId.toString(),
                                        3L,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        blockId.toString(),
                                        3L,
                                        null,
                                        parentId.toString(),
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-3",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        blockId.toString(),
                                        3L,
                                        replacedAgainContent,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).extracting(EditorSaveAppliedOperationResult::version)
                .containsExactly(4L, 5L, 6L);

        verify(blockService).update(blockId, REPLACED_CONTENT, 3, ACTOR_ID);
        verify(blockService).move(blockId, parentId, null, null, 4, ACTOR_ID);
        verify(blockService).update(blockId, replacedAgainContent, 5, ACTOR_ID);
    }

    @Test
    @DisplayName("성공_real block replace_content, move 뒤 delete는 같은 base version으로 이어서 처리한다")
    void applyUsesServerManagedVersionForDeleteAfterReplaceAndMoveOnSameRealBlock() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 3);
        Block updatedBlock = block(blockId, documentId, "000000000001000000000000", 4);
        updatedBlock.setContent(REPLACED_CONTENT);
        Block movedBlock = block(blockId, documentId, "000000000001I00000000000", 5);
        movedBlock.setContent(REPLACED_CONTENT);
        Block deletedBlock = block(blockId, documentId, "000000000001I00000000000", 5);
        deletedBlock.setDeletedAt(LocalDateTime.now());

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId))
                .thenReturn(Optional.of(existingBlock))
                .thenReturn(Optional.of(movedBlock));
        when(blockService.update(blockId, REPLACED_CONTENT, 3, ACTOR_ID)).thenReturn(updatedBlock);
        when(blockService.move(blockId, parentId, null, null, 4, ACTOR_ID)).thenReturn(movedBlock);
        when(blockService.delete(blockId, 5, ACTOR_ID)).thenReturn(deletedBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-real-block-delete-chain",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        blockId.toString(),
                                        3L,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        blockId.toString(),
                                        3L,
                                        null,
                                        parentId.toString(),
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-3",
                                        EditorSaveOperationType.BLOCK_DELETE,
                                        blockId.toString(),
                                        3L,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).extracting(EditorSaveAppliedOperationResult::status)
                .containsExactly(
                        EditorSaveOperationStatus.APPLIED,
                        EditorSaveOperationStatus.APPLIED,
                        EditorSaveOperationStatus.APPLIED
                );

        verify(blockService).update(blockId, REPLACED_CONTENT, 3, ACTOR_ID);
        verify(blockService).move(blockId, parentId, null, null, 4, ACTOR_ID);
        verify(blockService).delete(blockId, 5, ACTOR_ID);
    }

    @Test
    @DisplayName("실패_real block이 같은 batch 안에서 다른 base version을 섞어 보내면 충돌 예외를 던진다")
    void applyThrowsWhenRealBlockUsesDifferentBaseVersionInsideBatch() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 3);
        Block updatedBlock = block(blockId, documentId, "000000000001000000000000", 4);
        updatedBlock.setContent(REPLACED_CONTENT);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId))
                .thenReturn(Optional.of(existingBlock));
        when(blockService.update(blockId, REPLACED_CONTENT, 3, ACTOR_ID)).thenReturn(updatedBlock);

        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-inconsistent-base-version",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        blockId.toString(),
                                        3L,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        blockId.toString(),
                                        4L,
                                        null,
                                        parentId.toString(),
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("실패_create에서 같은 blockRef를 중복 사용하면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenCreateUsesDuplicateBlockReference() {
        UUID documentId = UUID.randomUUID();
        Block createdBlock = block(UUID.randomUUID(), documentId, "000000000001000000000000", 0);

        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdBlock);

        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);

        verify(blockService, never()).update(any(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("실패_existing block replace_content에 version이 없으면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenReplaceContentOmitsVersionForExistingBlock() {
        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                UUID.randomUUID(),
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        UUID.randomUUID().toString(),
                                        null,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_existing block move에 version이 없으면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenMoveOmitsVersionForExistingBlock() {
        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                UUID.randomUUID(),
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        UUID.randomUUID().toString(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("성공_create 뒤 temp block delete는 version 없이 같은 batch에서 처리한다")
    void applyDeletesCreatedTempBlockWithoutVersion() {
        UUID documentId = UUID.randomUUID();
        UUID createdBlockId = UUID.randomUUID();

        Block createdBlock = block(createdBlockId, documentId, "000000000001000000000000", 0);
        Block deletedBlock = block(createdBlockId, documentId, "000000000001000000000000", 0);
        deletedBlock.setDeletedAt(LocalDateTime.now());

        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdBlock);
        when(blockRepository.findByIdAndDeletedAtIsNull(createdBlockId)).thenReturn(Optional.of(createdBlock));
        when(blockService.delete(createdBlockId, 0, ACTOR_ID)).thenReturn(deletedBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-temp-delete",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_DELETE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).extracting(EditorSaveAppliedOperationResult::status)
                .containsExactly(EditorSaveOperationStatus.APPLIED, EditorSaveOperationStatus.APPLIED);
        verify(blockService).delete(createdBlockId, 0, ACTOR_ID);
    }

    @Test
    @DisplayName("성공_create 뒤 replace_content 후 temp block delete는 currentVersion으로 처리한다")
    void applyDeletesCreatedTempBlockAfterReplaceContent() {
        UUID documentId = UUID.randomUUID();
        UUID createdBlockId = UUID.randomUUID();

        Block createdBlock = block(createdBlockId, documentId, "000000000001000000000000", 0);
        Block updatedBlock = block(createdBlockId, documentId, "000000000001000000000000", 1);
        updatedBlock.setContent(REPLACED_CONTENT);
        Block deletedBlock = block(createdBlockId, documentId, "000000000001000000000000", 1);
        deletedBlock.setDeletedAt(LocalDateTime.now());

        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdBlock);
        when(blockService.update(createdBlockId, REPLACED_CONTENT, 0, ACTOR_ID)).thenReturn(updatedBlock);
        when(blockRepository.findByIdAndDeletedAtIsNull(createdBlockId)).thenReturn(Optional.of(updatedBlock));
        when(blockService.delete(createdBlockId, 1, ACTOR_ID)).thenReturn(deletedBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-temp-replace-delete",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        "tmp:block:1",
                                        null,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-3",
                                        EditorSaveOperationType.BLOCK_DELETE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).extracting(EditorSaveAppliedOperationResult::status)
                .containsExactly(
                        EditorSaveOperationStatus.APPLIED,
                        EditorSaveOperationStatus.APPLIED,
                        EditorSaveOperationStatus.APPLIED
                );
        verify(blockService).delete(createdBlockId, 1, ACTOR_ID);
    }

    @Test
    @DisplayName("성공_create 뒤 move 후 temp block delete는 currentVersion으로 처리한다")
    void applyDeletesCreatedTempBlockAfterMove() {
        UUID documentId = UUID.randomUUID();
        UUID createdBlockId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        Block createdBlock = block(createdBlockId, documentId, "000000000001000000000000", 0);
        Block movedBlock = block(createdBlockId, documentId, "000000000001I00000000000", 1);
        Block deletedBlock = block(createdBlockId, documentId, "000000000001I00000000000", 1);
        deletedBlock.setDeletedAt(LocalDateTime.now());

        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdBlock);
        when(blockService.move(createdBlockId, parentId, null, null, 0, ACTOR_ID)).thenReturn(movedBlock);
        when(blockRepository.findByIdAndDeletedAtIsNull(createdBlockId)).thenReturn(Optional.of(movedBlock));
        when(blockService.delete(createdBlockId, 1, ACTOR_ID)).thenReturn(deletedBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-temp-move-delete",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        parentId.toString(),
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-3",
                                        EditorSaveOperationType.BLOCK_DELETE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).extracting(EditorSaveAppliedOperationResult::status)
                .containsExactly(
                        EditorSaveOperationStatus.APPLIED,
                        EditorSaveOperationStatus.APPLIED,
                        EditorSaveOperationStatus.APPLIED
                );
        verify(blockService).delete(createdBlockId, 1, ACTOR_ID);
    }

    @Test
    @DisplayName("실패_temp block delete에 version이 오면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenTempDeleteIncludesVersion() {
        UUID documentId = UUID.randomUUID();
        UUID createdBlockId = UUID.randomUUID();
        Block createdBlock = block(createdBlockId, documentId, "000000000001000000000000", 0);

        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdBlock);

        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-temp-delete-with-version",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_DELETE,
                                        "tmp:block:1",
                                        0L,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_existing block delete에 version이 없으면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenDeleteOmitsVersionForExistingBlock() {
        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                UUID.randomUUID(),
                new EditorSaveCommand(
                        "web-editor",
                        "batch-delete-without-version",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_DELETE,
                                        UUID.randomUUID().toString(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_temp block delete 뒤 같은 temp replace_content를 참조하면 블록 없음 예외를 던진다")
    void applyThrowsWhenReplaceContentReferencesTempBlockDeletedEarlierInBatch() {
        UUID documentId = UUID.randomUUID();
        UUID createdBlockId = UUID.randomUUID();
        Block createdBlock = block(createdBlockId, documentId, "000000000001000000000000", 0);
        Block deletedBlock = block(createdBlockId, documentId, "000000000001000000000000", 0);
        deletedBlock.setDeletedAt(LocalDateTime.now());

        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdBlock);
        when(blockRepository.findByIdAndDeletedAtIsNull(createdBlockId))
                .thenReturn(Optional.of(createdBlock))
                .thenReturn(Optional.empty());
        when(blockService.delete(createdBlockId, 0, ACTOR_ID)).thenReturn(deletedBlock);
        when(blockService.update(createdBlockId, REPLACED_CONTENT, 0, ACTOR_ID))
                .thenThrow(new BusinessException(BusinessErrorCode.BLOCK_NOT_FOUND));

        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-temp-delete-then-replace",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_DELETE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-3",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        "tmp:block:1",
                                        null,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.BLOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("실패_temp block delete 뒤 같은 temp move를 참조하면 블록 없음 예외를 던진다")
    void applyThrowsWhenMoveReferencesTempBlockDeletedEarlierInBatch() {
        UUID documentId = UUID.randomUUID();
        UUID createdBlockId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Block createdBlock = block(createdBlockId, documentId, "000000000001000000000000", 0);
        Block deletedBlock = block(createdBlockId, documentId, "000000000001000000000000", 0);
        deletedBlock.setDeletedAt(LocalDateTime.now());

        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdBlock);
        when(blockRepository.findByIdAndDeletedAtIsNull(createdBlockId))
                .thenReturn(Optional.of(createdBlock))
                .thenReturn(Optional.empty());
        when(blockService.delete(createdBlockId, 0, ACTOR_ID)).thenReturn(deletedBlock);
        when(blockService.move(createdBlockId, parentId, null, null, 0, ACTOR_ID))
                .thenThrow(new BusinessException(BusinessErrorCode.BLOCK_NOT_FOUND));

        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-temp-delete-then-move",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_DELETE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-3",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        parentId.toString(),
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.BLOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("성공_replace_content no-op 뒤 real block delete는 증가하지 않은 version으로 처리한다")
    void applyDeletesExistingBlockAfterReplaceContentNoOp() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 3);
        existingBlock.setContent(REPLACED_CONTENT);
        Block deletedBlock = block(blockId, documentId, "000000000001000000000000", 3);
        deletedBlock.setDeletedAt(LocalDateTime.now());

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId))
                .thenReturn(Optional.of(existingBlock))
                .thenReturn(Optional.of(existingBlock));
        when(blockService.update(blockId, REPLACED_CONTENT, 3, ACTOR_ID)).thenReturn(existingBlock);
        when(blockService.delete(blockId, 3, ACTOR_ID)).thenReturn(deletedBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-replace-no-op-delete",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        blockId.toString(),
                                        3L,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_DELETE,
                                        blockId.toString(),
                                        3L,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).extracting(EditorSaveAppliedOperationResult::status)
                .containsExactly(EditorSaveOperationStatus.NO_OP, EditorSaveOperationStatus.APPLIED);
        verify(blockService).delete(blockId, 3, ACTOR_ID);
    }

    @Test
    @DisplayName("성공_move no-op 뒤 real block delete는 증가하지 않은 version으로 처리한다")
    void applyDeletesExistingBlockAfterMoveNoOp() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 4);
        Block deletedBlock = block(blockId, documentId, "000000000001000000000000", 4);
        deletedBlock.setDeletedAt(LocalDateTime.now());

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId))
                .thenReturn(Optional.of(existingBlock))
                .thenReturn(Optional.of(existingBlock));
        when(blockService.move(blockId, null, null, null, 4, ACTOR_ID)).thenReturn(existingBlock);
        when(blockService.delete(blockId, 4, ACTOR_ID)).thenReturn(deletedBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-move-no-op-delete",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        blockId.toString(),
                                        4L,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_DELETE,
                                        blockId.toString(),
                                        4L,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).extracting(EditorSaveAppliedOperationResult::status)
                .containsExactly(EditorSaveOperationStatus.NO_OP, EditorSaveOperationStatus.APPLIED);
        verify(blockService).delete(blockId, 4, ACTOR_ID);
    }

    @Test
    @DisplayName("실패_existing block replace_content에 잘못된 blockRef가 오면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenReplaceContentUsesInvalidExistingBlockReference() {
        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                UUID.randomUUID(),
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        "not-a-uuid",
                                        0L,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_create 뒤 replace_content가 충돌하면 예외를 전파한다")
    void applyPropagatesConflictWhenReplaceContentFailsAfterCreate() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block createdBlock = block(blockId, documentId, "000000000001000000000000", 0);

        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdBlock);
        when(blockService.update(blockId, REPLACED_CONTENT, 0, ACTOR_ID))
                .thenThrow(new BusinessException(BusinessErrorCode.CONFLICT));

        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        "tmp:block:1",
                                        null,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("성공_existing block replace_content는 전달받은 version으로 update를 호출한다")
    void applyReplacesExistingBlockWithRequestedVersion() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 3);
        Block updatedBlock = block(blockId, documentId, "000000000001000000000000", 4);
        updatedBlock.setContent(REPLACED_CONTENT);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(existingBlock));
        when(blockService.update(blockId, REPLACED_CONTENT, 3, ACTOR_ID)).thenReturn(updatedBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        blockId.toString(),
                                        3L,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).hasSize(1);
        assertThat(result.appliedOperations().get(0).blockId()).isEqualTo(blockId);
        assertThat(result.appliedOperations().get(0).version()).isEqualTo(4);

        verify(blockService).update(blockId, REPLACED_CONTENT, 3, ACTOR_ID);
    }

    @Test
    @DisplayName("성공_replace_content가 no-op이면 NO_OP status와 기존 version을 반환한다")
    void applyReturnsNoOpStatusWhenReplaceContentDoesNotChangeBlockState() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 3);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(existingBlock));
        when(blockService.update(blockId, REPLACED_CONTENT, 3, ACTOR_ID)).thenReturn(existingBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-no-op",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        blockId.toString(),
                                        3L,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).hasSize(1);
        assertThat(result.appliedOperations().get(0).status()).isEqualTo(EditorSaveOperationStatus.NO_OP);
        assertThat(result.appliedOperations().get(0).version()).isEqualTo(3);
    }

    @Test
    @DisplayName("성공_replace_content no-op 뒤 move는 증가하지 않은 version으로 후속 move를 호출한다")
    void applyUsesUnchangedVersionAfterReplaceContentNoOp() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 3);
        Block movedBlock = block(blockId, documentId, "000000000001I00000000000", 4);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId))
                .thenReturn(Optional.of(existingBlock))
                .thenReturn(Optional.of(existingBlock));
        when(blockService.update(blockId, REPLACED_CONTENT, 3, ACTOR_ID)).thenReturn(existingBlock);
        when(blockService.move(blockId, parentId, null, null, 3, ACTOR_ID)).thenReturn(movedBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-no-op-replace-then-move",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        blockId.toString(),
                                        3L,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_MOVE,
                                        blockId.toString(),
                                        3L,
                                        null,
                                        parentId.toString(),
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations().get(0).status()).isEqualTo(EditorSaveOperationStatus.NO_OP);
        assertThat(result.appliedOperations().get(1).version()).isEqualTo(4);

        verify(blockService).update(blockId, REPLACED_CONTENT, 3, ACTOR_ID);
        verify(blockService).move(blockId, parentId, null, null, 3, ACTOR_ID);
    }

    @Test
    @DisplayName("성공_create 뒤 replace_content를 두 번 적용하면 temp block context version을 갱신한다")
    void applyUpdatesTempBlockVersionAcrossConsecutiveReplaceContentOperations() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        String secondContent =
                "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"두 번째 수정\",\"marks\":[]}]}";

        Block createdBlock = block(blockId, documentId, "000000000001000000000000", 0);
        Block firstUpdatedBlock = block(blockId, documentId, "000000000001000000000000", 1);
        firstUpdatedBlock.setContent(REPLACED_CONTENT);
        Block secondUpdatedBlock = block(blockId, documentId, "000000000001000000000000", 2);
        secondUpdatedBlock.setContent(secondContent);

        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdBlock);
        when(blockService.update(blockId, REPLACED_CONTENT, 0, ACTOR_ID)).thenReturn(firstUpdatedBlock);
        when(blockService.update(blockId, secondContent, 1, ACTOR_ID)).thenReturn(secondUpdatedBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        "tmp:block:1",
                                        null,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-3",
                                        EditorSaveOperationType.BLOCK_REPLACE_CONTENT,
                                        "tmp:block:1",
                                        null,
                                        secondContent,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).extracting(EditorSaveAppliedOperationResult::version)
                .containsExactly(0L, 1L, 2L);

        verify(blockService).update(blockId, REPLACED_CONTENT, 0, ACTOR_ID);
        verify(blockService).update(blockId, secondContent, 1, ACTOR_ID);
    }

    @Test
    @DisplayName("성공_create는 temp parentRef를 실제 parentId로 해석한다")
    void applyResolvesTempParentReferenceForCreate() {
        UUID documentId = UUID.randomUUID();
        UUID parentBlockId = UUID.randomUUID();
        UUID childBlockId = UUID.randomUUID();

        Block createdParentBlock = block(parentBlockId, documentId, "000000000001000000000000", 0);
        Block createdChildBlock = block(childBlockId, documentId, "000000000001I00000000000", 0);

        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdParentBlock);
        when(blockService.create(
                any(Document.class),
                eq(parentBlockId),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdChildBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:parent",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:child",
                                        null,
                                        null,
                                        "tmp:parent",
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).extracting(EditorSaveAppliedOperationResult::blockId)
                .containsExactly(parentBlockId, childBlockId);
    }

    @Test
    @DisplayName("성공_create는 temp afterRef와 beforeRef를 실제 anchor id로 해석한다")
    void applyResolvesTempSiblingAnchorsForCreate() {
        UUID documentId = UUID.randomUUID();
        UUID firstBlockId = UUID.randomUUID();
        UUID middleBlockId = UUID.randomUUID();
        UUID beforeBlockId = UUID.randomUUID();

        Block createdFirstBlock = block(firstBlockId, documentId, "000000000001000000000000", 0);
        Block createdMiddleBlock = block(middleBlockId, documentId, "000000000001I00000000000", 0);
        Block createdBeforeBlock = block(beforeBlockId, documentId, "000000000001Q00000000000", 0);

        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdFirstBlock, createdBeforeBlock);
        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(firstBlockId),
                eq(beforeBlockId),
                eq(ACTOR_ID)
        )).thenReturn(createdMiddleBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:first",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:before",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-3",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:middle",
                                        null,
                                        null,
                                        null,
                                        "tmp:first",
                                        "tmp:before"
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).extracting(EditorSaveAppliedOperationResult::blockId)
                .containsExactly(firstBlockId, beforeBlockId, middleBlockId);
    }

    @Test
    @DisplayName("실패_create의 parentRef가 존재하지 않는 temp를 가리키면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenCreateUsesUnknownTempParentReference() {
        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                UUID.randomUUID(),
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:child",
                                        null,
                                        null,
                                        "tmp:missing-parent",
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_create의 afterRef가 존재하지 않는 temp를 가리키면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenCreateUsesUnknownTempAfterReference() {
        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                UUID.randomUUID(),
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:block",
                                        null,
                                        null,
                                        null,
                                        "tmp:missing-after",
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_create의 beforeRef가 존재하지 않는 temp를 가리키면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenCreateUsesUnknownTempBeforeReference() {
        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                UUID.randomUUID(),
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:block",
                                        null,
                                        null,
                                        null,
                                        null,
                                        "tmp:missing-before"
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_create가 아직 생성되지 않은 temp ref를 먼저 참조하면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenCreateReferencesFutureTempAnchor() {
        assertThatThrownBy(() -> editorOperationOrchestrator.save(
                UUID.randomUUID(),
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:middle",
                                        null,
                                        null,
                                        null,
                                        "tmp:first",
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:first",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("성공_create는 real afterRef와 temp beforeRef를 함께 해석한다")
    void applyResolvesMixedRealAndTempAnchorsForCreate() {
        UUID documentId = UUID.randomUUID();
        UUID realAfterBlockId = UUID.randomUUID();
        UUID tempBeforeBlockId = UUID.randomUUID();
        UUID middleBlockId = UUID.randomUUID();

        Block createdTempBeforeBlock = block(tempBeforeBlockId, documentId, "000000000002000000000000", 0);
        Block createdMiddleBlock = block(middleBlockId, documentId, "000000000001I00000000000", 0);

        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdTempBeforeBlock);
        when(blockService.create(
                any(Document.class),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(realAfterBlockId),
                eq(tempBeforeBlockId),
                eq(ACTOR_ID)
        )).thenReturn(createdMiddleBlock);

        EditorSaveResult result = editorOperationOrchestrator.save(
                documentId,
                new EditorSaveCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new EditorSaveOperationCommand(
                                        "op-1",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:before",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new EditorSaveOperationCommand(
                                        "op-2",
                                        EditorSaveOperationType.BLOCK_CREATE,
                                        "tmp:middle",
                                        null,
                                        null,
                                        null,
                                        realAfterBlockId.toString(),
                                        "tmp:before"
                                )
                        )
                ),
                ACTOR_ID
        );

        assertThat(result.appliedOperations()).extracting(EditorSaveAppliedOperationResult::blockId)
                .containsExactly(tempBeforeBlockId, middleBlockId);
    }

    @Test
    @DisplayName("성공_document move는 문서를 이동하고 최신 documentVersion을 응답한다")
    void moveReturnsDocumentMoveResult() {
        UUID documentId = UUID.randomUUID();
        UUID targetParentId = UUID.randomUUID();

        Document movedDocument = document(documentId, 1);
        movedDocument.setParent(Document.builder().id(targetParentId).build());

        when(documentService.move(documentId, targetParentId, null, null, ACTOR_ID)).thenReturn(movedDocument);
        EditorMoveResult result = editorOperationOrchestrator.move(
                new EditorMoveCommand(
                        EditorMoveResourceType.DOCUMENT,
                        documentId,
                        targetParentId,
                        null,
                        null,
                        null
                ),
                ACTOR_ID
        );

        assertThat(result.resourceType()).isEqualTo(EditorMoveResourceType.DOCUMENT);
        assertThat(result.resourceId()).isEqualTo(documentId);
        assertThat(result.parentId()).isEqualTo(targetParentId);
        assertThat(result.version()).isEqualTo(1L);
        assertThat(result.documentVersion()).isEqualTo(1L);

        verify(documentService).move(documentId, targetParentId, null, null, ACTOR_ID);
    }

    @Test
    @DisplayName("성공_block move는 블록을 이동하고 최신 block/document version을 응답한다")
    void moveReturnsBlockMoveResult() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID targetParentId = UUID.randomUUID();

        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 2);
        Block movedBlock = block(blockId, documentId, "000000000001I00000000000", 3);
        movedBlock.setParent(Block.builder().id(targetParentId).build());

        Document document = document(documentId, 4);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(existingBlock));
        when(blockService.move(blockId, targetParentId, null, null, 2, ACTOR_ID)).thenReturn(movedBlock);
        when(documentService.getById(documentId)).thenReturn(document);

        EditorMoveResult result = editorOperationOrchestrator.move(
                new EditorMoveCommand(
                        EditorMoveResourceType.BLOCK,
                        blockId,
                        targetParentId,
                        null,
                        null,
                        2L
                ),
                ACTOR_ID
        );

        assertThat(result.resourceType()).isEqualTo(EditorMoveResourceType.BLOCK);
        assertThat(result.resourceId()).isEqualTo(blockId);
        assertThat(result.parentId()).isEqualTo(targetParentId);
        assertThat(result.version()).isEqualTo(3L);
        assertThat(result.documentVersion()).isEqualTo(4L);
        assertThat(result.sortKey()).isEqualTo("000000000001I00000000000");

        verify(blockService).move(blockId, targetParentId, null, null, 2, ACTOR_ID);
    }

    @Test
    @DisplayName("성공_block move no-op은 증가하지 않은 block/document version을 응답한다")
    void moveReturnsCurrentVersionsWhenBlockMoveIsNoOp() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 2);
        Document document = document(documentId, 5);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(existingBlock));
        when(blockService.move(blockId, null, null, null, 2, ACTOR_ID)).thenReturn(existingBlock);
        when(documentService.getById(documentId)).thenReturn(document);

        EditorMoveResult result = editorOperationOrchestrator.move(
                new EditorMoveCommand(
                        EditorMoveResourceType.BLOCK,
                        blockId,
                        null,
                        null,
                        null,
                        2L
                ),
                ACTOR_ID
        );

        assertThat(result.resourceType()).isEqualTo(EditorMoveResourceType.BLOCK);
        assertThat(result.resourceId()).isEqualTo(blockId);
        assertThat(result.parentId()).isNull();
        assertThat(result.version()).isEqualTo(2L);
        assertThat(result.documentVersion()).isEqualTo(5L);
        assertThat(result.sortKey()).isEqualTo("000000000001000000000000");
    }

    @Test
    @DisplayName("실패_document move에서 문서 서비스 예외가 발생하면 그대로 전파한다")
    void movePropagatesDocumentMoveException() {
        UUID documentId = UUID.randomUUID();

        when(documentService.move(documentId, null, null, null, ACTOR_ID))
                .thenThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));

        assertThatThrownBy(() -> editorOperationOrchestrator.move(
                new EditorMoveCommand(
                        EditorMoveResourceType.DOCUMENT,
                        documentId,
                        null,
                        null,
                        null,
                        null
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.DOCUMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("실패_block move에서 stale version 충돌이 발생하면 그대로 전파한다")
    void movePropagatesBlockMoveConflict() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        Block existingBlock = block(blockId, documentId, "000000000001000000000000", 2);

        when(blockRepository.findByIdAndDeletedAtIsNull(blockId)).thenReturn(Optional.of(existingBlock));
        when(blockService.move(blockId, null, null, null, 2, ACTOR_ID))
                .thenThrow(new BusinessException(BusinessErrorCode.CONFLICT));

        assertThatThrownBy(() -> editorOperationOrchestrator.move(
                new EditorMoveCommand(
                        EditorMoveResourceType.BLOCK,
                        blockId,
                        null,
                        null,
                        null,
                        2L
                ),
                ACTOR_ID
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.CONFLICT);
    }

    private Block block(UUID blockId, UUID documentId, String sortKey, int version) {
        Block block = Block.builder()
                .id(blockId)
                .document(Document.builder().id(documentId).build())
                .type(BlockType.TEXT)
                .content(EMPTY_BLOCK_CONTENT)
                .sortKey(sortKey)
                .createdBy(ACTOR_ID)
                .updatedBy(ACTOR_ID)
                .build();
        block.setVersion(version);
        return block;
    }

    private Document document(UUID documentId, int version) {
        Document document = Document.builder()
                .id(documentId)
                .title("문서")
                .sortKey("00000000000000000001")
                .build();
        document.setVersion(version);
        return document;
    }
}
