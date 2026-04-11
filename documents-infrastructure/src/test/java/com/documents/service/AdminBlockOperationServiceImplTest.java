package com.documents.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.documents.domain.Block;
import com.documents.domain.Document;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.service.editor.EditorSaveAppliedOperationResult;
import com.documents.service.editor.EditorSaveOperationCommand;
import com.documents.service.editor.EditorSaveOperationStatus;
import com.documents.service.editor.EditorSaveOperationType;
import com.documents.service.editor.EditorSaveResult;
import com.documents.service.transaction.DocumentVersionUpdater;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminBlockOperationService 구현 검증")
class AdminBlockOperationServiceImplTest {

    private static final String ACTOR_ID = "user-123";

    @Mock
    private BlockService blockService;

    @Mock
    private DocumentService documentService;

    @Mock
    private EditorSaveOperationExecutor operationExecutor;

    @Mock
    private DocumentVersionUpdater documentVersionUpdater;

    @InjectMocks
    private AdminBlockOperationServiceImpl adminBlockOperationService;

    @Test
    @DisplayName("성공_create는 단건 operation 적용 후 문서 version 증가 응답을 반환한다")
    void applyCreateReturnsSaveResultAndIncrementsDocumentVersion() {
        UUID documentId = UUID.randomUUID();
        Document document = document(documentId, 3);
        EditorSaveOperationCommand operation = operation("tmp:block:1", EditorSaveOperationType.BLOCK_CREATE, null);
        UUID blockId = UUID.randomUUID();

        when(documentService.getById(documentId)).thenReturn(document);
        when(operationExecutor.apply(eq(documentId), eq(document), eq(operation), eq(ACTOR_ID), any()))
                .thenReturn(new EditorSaveAppliedOperationResult(
                        "op-1",
                        EditorSaveOperationStatus.APPLIED,
                        "tmp:block:1",
                        blockId,
                        0L,
                        "000000000001000000000000",
                        null
                ));
        when(documentVersionUpdater.increment(eq(documentId), eq(ACTOR_ID), any(LocalDateTime.class)))
                .thenReturn(document(documentId, 4));

        EditorSaveResult result = adminBlockOperationService.applyCreate(documentId, "batch-1", operation, ACTOR_ID);

        assertThat(result.documentId()).isEqualTo(documentId);
        assertThat(result.documentVersion()).isEqualTo(4L);
        assertThat(result.batchId()).isEqualTo("batch-1");
        assertThat(result.appliedOperations()).hasSize(1);
        assertThat(result.appliedOperations().get(0).blockId()).isEqualTo(blockId);

        verify(documentVersionUpdater).increment(eq(documentId), eq(ACTOR_ID), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("성공_replace_content가 no-op이면 문서 version을 증가시키지 않는다")
    void applyReplaceContentDoesNotIncrementDocumentVersionWhenNoOp() {
        UUID blockId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Block block = block(blockId, documentId, 5);
        Document document = document(documentId, 7);
        EditorSaveOperationCommand operation = operation(blockId.toString(), EditorSaveOperationType.BLOCK_REPLACE_CONTENT, 4L);

        when(blockService.getById(blockId)).thenReturn(block);
        when(documentService.getById(documentId)).thenReturn(document);
        when(operationExecutor.apply(eq(documentId), eq(document), eq(operation), eq(ACTOR_ID), any()))
                .thenReturn(new EditorSaveAppliedOperationResult(
                        "op-1",
                        EditorSaveOperationStatus.NO_OP,
                        null,
                        blockId,
                        5L,
                        "000000000001000000000000",
                        null
                ));

        EditorSaveResult result = adminBlockOperationService.applyReplaceContent(blockId, "batch-2", operation, ACTOR_ID);

        assertThat(result.documentVersion()).isEqualTo(7L);
        verify(documentVersionUpdater, never()).increment(any(UUID.class), any(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("실패_replace_content는 block 조회가 먼저여서 없는 block이면 type 검증 전 not found를 반환한다")
    void applyReplaceContentThrowsBlockNotFoundBeforeTypeValidation() {
        UUID blockId = UUID.randomUUID();
        EditorSaveOperationCommand invalidTypeOperation = operation(blockId.toString(), EditorSaveOperationType.BLOCK_MOVE, 0L);

        when(blockService.getById(blockId))
                .thenThrow(new BusinessException(BusinessErrorCode.BLOCK_NOT_FOUND));

        assertThatThrownBy(() -> adminBlockOperationService.applyReplaceContent(blockId, "batch-3", invalidTypeOperation, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.BLOCK_NOT_FOUND);

        verify(operationExecutor, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("실패_move는 block 조회 후 type이 다르면 잘못된 요청을 반환한다")
    void applyMoveRejectsUnexpectedOperationTypeAfterBlockLookup() {
        UUID blockId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(blockService.getById(blockId)).thenReturn(block(blockId, documentId, 1));

        EditorSaveOperationCommand invalidTypeOperation = operation(blockId.toString(), EditorSaveOperationType.BLOCK_DELETE, 0L);

        assertThatThrownBy(() -> adminBlockOperationService.applyMove(blockId, "batch-4", invalidTypeOperation, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);

        verify(operationExecutor, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("실패_delete는 blockRef가 path와 다르면 잘못된 요청을 반환한다")
    void applyDeleteRejectsMismatchedBlockReference() {
        UUID blockId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(blockService.getById(blockId)).thenReturn(block(blockId, documentId, 2));

        EditorSaveOperationCommand operation = operation(UUID.randomUUID().toString(), EditorSaveOperationType.BLOCK_DELETE, 1L);

        assertThatThrownBy(() -> adminBlockOperationService.applyDelete(blockId, "batch-5", operation, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);

        verify(operationExecutor, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("실패_create는 문서를 찾지 못하면 document not found를 반환한다")
    void applyCreateThrowsDocumentNotFound() {
        UUID documentId = UUID.randomUUID();
        EditorSaveOperationCommand operation = operation("tmp:block:1", EditorSaveOperationType.BLOCK_CREATE, null);

        when(documentService.getById(documentId))
                .thenThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));

        assertThatThrownBy(() -> adminBlockOperationService.applyCreate(documentId, "batch-6", operation, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.DOCUMENT_NOT_FOUND);

        verify(operationExecutor, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("성공_move가 no-op이면 문서 version을 증가시키지 않는다")
    void applyMoveDoesNotIncrementDocumentVersionWhenNoOp() {
        UUID blockId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Block block = block(blockId, documentId, 5);
        Document document = document(documentId, 9);
        EditorSaveOperationCommand operation = operation(blockId.toString(), EditorSaveOperationType.BLOCK_MOVE, 4L);

        when(blockService.getById(blockId)).thenReturn(block);
        when(documentService.getById(documentId)).thenReturn(document);
        when(operationExecutor.apply(eq(documentId), eq(document), eq(operation), eq(ACTOR_ID), any()))
                .thenReturn(new EditorSaveAppliedOperationResult(
                        "op-1",
                        EditorSaveOperationStatus.NO_OP,
                        null,
                        blockId,
                        5L,
                        "000000000001000000000000",
                        null
                ));

        EditorSaveResult result = adminBlockOperationService.applyMove(blockId, "batch-7", operation, ACTOR_ID);

        assertThat(result.documentVersion()).isEqualTo(9L);
        verify(documentVersionUpdater, never()).increment(any(UUID.class), any(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("실패_적용은 성공했지만 문서 version 증가에 실패하면 충돌을 반환한다")
    void applyDeleteThrowsConflictWhenVersionIncrementFails() {
        UUID blockId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Block block = block(blockId, documentId, 3);
        Document document = document(documentId, 6);
        EditorSaveOperationCommand operation = operation(blockId.toString(), EditorSaveOperationType.BLOCK_DELETE, 2L);

        when(blockService.getById(blockId)).thenReturn(block);
        when(documentService.getById(documentId)).thenReturn(document);
        when(operationExecutor.apply(eq(documentId), eq(document), eq(operation), eq(ACTOR_ID), any()))
                .thenReturn(new EditorSaveAppliedOperationResult(
                        "op-1",
                        EditorSaveOperationStatus.APPLIED,
                        null,
                        blockId,
                        null,
                        null,
                        LocalDateTime.of(2026, 4, 10, 0, 0)
                ));
        when(documentVersionUpdater.increment(eq(documentId), eq(ACTOR_ID), any(LocalDateTime.class)))
                .thenThrow(new BusinessException(BusinessErrorCode.CONFLICT));

        assertThatThrownBy(() -> adminBlockOperationService.applyDelete(blockId, "batch-8", operation, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.CONFLICT);
    }

    private EditorSaveOperationCommand operation(String blockReference, EditorSaveOperationType type, Long version) {
        return new EditorSaveOperationCommand(
                "op-1",
                type,
                blockReference,
                version,
                "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"내용\",\"marks\":[]}]}",
                null,
                null,
                null
        );
    }

    private Document document(UUID documentId, Integer version) {
        Document document = Document.builder()
                .id(documentId)
                .title("문서")
                .sortKey("00000000000000000001")
                .createdBy(ACTOR_ID)
                .updatedBy(ACTOR_ID)
                .build();
        document.setVersion(version);
        return document;
    }

    private Block block(UUID blockId, UUID documentId, Integer version) {
        Block block = Block.builder()
                .id(blockId)
                .document(Document.builder()
                        .id(documentId)
                        .title("문서")
                        .sortKey("00000000000000000001")
                        .createdBy(ACTOR_ID)
                        .updatedBy(ACTOR_ID)
                        .build())
                .content("{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"내용\",\"marks\":[]}]}")
                .sortKey("000000000001000000000000")
                .createdBy(ACTOR_ID)
                .updatedBy(ACTOR_ID)
                .build();
        block.setVersion(version);
        return block;
    }
}
