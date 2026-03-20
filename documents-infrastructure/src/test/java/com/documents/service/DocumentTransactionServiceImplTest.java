package com.documents.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.documents.domain.Block;
import com.documents.domain.BlockType;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.BlockRepository;
import com.documents.service.transaction.DocumentTransactionAppliedOperationResult;
import com.documents.service.transaction.DocumentTransactionCommand;
import com.documents.service.transaction.DocumentTransactionOperationCommand;
import com.documents.service.transaction.DocumentTransactionOperationType;
import com.documents.service.transaction.DocumentTransactionResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Document transaction 서비스 구현 검증")
class DocumentTransactionServiceImplTest {

    private static final String ACTOR_ID = "user-123";
    private static final String EMPTY_BLOCK_CONTENT =
            "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"\",\"marks\":[]}]}";
    private static final String REPLACED_CONTENT =
            "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"새 블록\",\"marks\":[]}]}";

    @Mock
    private BlockService blockService;

    @Mock
    private BlockRepository blockRepository;

    private DocumentTransactionServiceImpl documentTransactionService;

    @BeforeEach
    void setUp() {
        documentTransactionService = new DocumentTransactionServiceImpl(blockService, blockRepository);
    }

    @Test
    @DisplayName("성공_create 후 tempId replace_content를 같은 transaction에서 반영한다")
    void applyCreatesBlockAndReplacesContent() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        Block createdBlock = block(blockId, documentId, "000000000001000000000000", 0);
        Block updatedBlock = block(blockId, documentId, "000000000001000000000000", 1);
        updatedBlock.setContent(REPLACED_CONTENT);

        when(blockService.create(
                eq(documentId),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdBlock);
        when(blockService.update(blockId, REPLACED_CONTENT, 0, ACTOR_ID)).thenReturn(updatedBlock);
        doNothing().when(blockRepository).flush();

        DocumentTransactionResult result = documentTransactionService.apply(
                documentId,
                new DocumentTransactionCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new DocumentTransactionOperationCommand(
                                        "op-1",
                                        DocumentTransactionOperationType.BLOCK_CREATE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new DocumentTransactionOperationCommand(
                                        "op-2",
                                        DocumentTransactionOperationType.BLOCK_REPLACE_CONTENT,
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
        assertThat(result.appliedOperations()).extracting(DocumentTransactionAppliedOperationResult::opId)
                .containsExactly("op-1", "op-2");
        assertThat(result.appliedOperations().get(0).tempId()).isEqualTo("tmp:block:1");
        assertThat(result.appliedOperations().get(0).blockId()).isEqualTo(blockId);
        assertThat(result.appliedOperations().get(1).blockId()).isEqualTo(blockId);
        assertThat(result.appliedOperations().get(1).version()).isEqualTo(1);

        verify(blockService).update(blockId, REPLACED_CONTENT, 0, ACTOR_ID);
    }

    @Test
    @DisplayName("실패_replace_content가 알 수 없는 tempId를 참조하면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenReplaceContentReferencesUnknownBlockReference() {
        assertThatThrownBy(() -> documentTransactionService.apply(
                UUID.randomUUID(),
                new DocumentTransactionCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new DocumentTransactionOperationCommand(
                                        "op-1",
                                        DocumentTransactionOperationType.BLOCK_REPLACE_CONTENT,
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
    @DisplayName("실패_create에서 같은 blockRef를 중복 사용하면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenCreateUsesDuplicateBlockReference() {
        UUID documentId = UUID.randomUUID();
        Block createdBlock = block(UUID.randomUUID(), documentId, "000000000001000000000000", 0);

        when(blockService.create(
                eq(documentId),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdBlock);
        doNothing().when(blockRepository).flush();

        assertThatThrownBy(() -> documentTransactionService.apply(
                documentId,
                new DocumentTransactionCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new DocumentTransactionOperationCommand(
                                        "op-1",
                                        DocumentTransactionOperationType.BLOCK_CREATE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new DocumentTransactionOperationCommand(
                                        "op-2",
                                        DocumentTransactionOperationType.BLOCK_CREATE,
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
        assertThatThrownBy(() -> documentTransactionService.apply(
                UUID.randomUUID(),
                new DocumentTransactionCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new DocumentTransactionOperationCommand(
                                        "op-1",
                                        DocumentTransactionOperationType.BLOCK_REPLACE_CONTENT,
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
    @DisplayName("실패_existing block replace_content에 잘못된 blockRef가 오면 잘못된 요청 예외를 던진다")
    void applyThrowsWhenReplaceContentUsesInvalidExistingBlockReference() {
        assertThatThrownBy(() -> documentTransactionService.apply(
                UUID.randomUUID(),
                new DocumentTransactionCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new DocumentTransactionOperationCommand(
                                        "op-1",
                                        DocumentTransactionOperationType.BLOCK_REPLACE_CONTENT,
                                        "not-a-uuid",
                                        0,
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
                eq(documentId),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdBlock);
        when(blockService.update(blockId, REPLACED_CONTENT, 0, ACTOR_ID))
                .thenThrow(new BusinessException(BusinessErrorCode.CONFLICT));
        doNothing().when(blockRepository).flush();

        assertThatThrownBy(() -> documentTransactionService.apply(
                documentId,
                new DocumentTransactionCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new DocumentTransactionOperationCommand(
                                        "op-1",
                                        DocumentTransactionOperationType.BLOCK_CREATE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new DocumentTransactionOperationCommand(
                                        "op-2",
                                        DocumentTransactionOperationType.BLOCK_REPLACE_CONTENT,
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
        Block updatedBlock = block(blockId, documentId, "000000000001000000000000", 4);
        updatedBlock.setContent(REPLACED_CONTENT);

        when(blockService.update(blockId, REPLACED_CONTENT, 3, ACTOR_ID)).thenReturn(updatedBlock);
        doNothing().when(blockRepository).flush();

        DocumentTransactionResult result = documentTransactionService.apply(
                documentId,
                new DocumentTransactionCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new DocumentTransactionOperationCommand(
                                        "op-1",
                                        DocumentTransactionOperationType.BLOCK_REPLACE_CONTENT,
                                        blockId.toString(),
                                        3,
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
                eq(documentId),
                eq(null),
                eq(BlockType.TEXT),
                eq(EMPTY_BLOCK_CONTENT),
                eq(null),
                eq(null),
                eq(ACTOR_ID)
        )).thenReturn(createdBlock);
        when(blockService.update(blockId, REPLACED_CONTENT, 0, ACTOR_ID)).thenReturn(firstUpdatedBlock);
        when(blockService.update(blockId, secondContent, 1, ACTOR_ID)).thenReturn(secondUpdatedBlock);
        doNothing().when(blockRepository).flush();

        DocumentTransactionResult result = documentTransactionService.apply(
                documentId,
                new DocumentTransactionCommand(
                        "web-editor",
                        "batch-1",
                        List.of(
                                new DocumentTransactionOperationCommand(
                                        "op-1",
                                        DocumentTransactionOperationType.BLOCK_CREATE,
                                        "tmp:block:1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                ),
                                new DocumentTransactionOperationCommand(
                                        "op-2",
                                        DocumentTransactionOperationType.BLOCK_REPLACE_CONTENT,
                                        "tmp:block:1",
                                        null,
                                        REPLACED_CONTENT,
                                        null,
                                        null,
                                        null
                                ),
                                new DocumentTransactionOperationCommand(
                                        "op-3",
                                        DocumentTransactionOperationType.BLOCK_REPLACE_CONTENT,
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

        assertThat(result.appliedOperations()).extracting(DocumentTransactionAppliedOperationResult::version)
                .containsExactly(0, 1, 2);

        verify(blockService).update(blockId, REPLACED_CONTENT, 0, ACTOR_ID);
        verify(blockService).update(blockId, secondContent, 1, ACTOR_ID);
    }

    private Block block(UUID blockId, UUID documentId, String sortKey, int version) {
        Block block = Block.builder()
                .id(blockId)
                .document(com.documents.domain.Document.builder().id(documentId).build())
                .type(BlockType.TEXT)
                .content(EMPTY_BLOCK_CONTENT)
                .sortKey(sortKey)
                .createdBy(ACTOR_ID)
                .updatedBy(ACTOR_ID)
                .build();
        block.setVersion(version);
        return block;
    }
}
