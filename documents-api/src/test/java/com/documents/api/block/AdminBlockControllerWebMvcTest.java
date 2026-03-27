package com.documents.api.block;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.documents.api.auth.CurrentUserIdArgumentResolver;
import com.documents.api.block.support.BlockJsonCodec;
import com.documents.api.document.DocumentTransactionApiMapper;
import com.documents.api.exception.GlobalExceptionHandler;
import com.documents.api.support.ApiResponseAssertions;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.service.AdminBlockTransactionService;
import com.documents.service.transaction.DocumentTransactionAppliedOperationResult;
import com.documents.service.transaction.DocumentTransactionOperationStatus;
import com.documents.service.transaction.DocumentTransactionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminBlock 컨트롤러 빠른 검증")
class AdminBlockControllerWebMvcTest {

    @Mock
    private AdminBlockTransactionService adminBlockTransactionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        BlockJsonCodec blockJsonCodec = new BlockJsonCodec(new ObjectMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminBlockController(
                        adminBlockTransactionService,
                        new DocumentTransactionApiMapper(blockJsonCodec)
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("성공_생성 API는 transaction create 요청을 그대로 위임한다")
    void createBlockDelegatesSingleCreateTransaction() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(adminBlockTransactionService.applyCreate(eq(documentId), any(), any(), eq("user-123")))
                .thenReturn(transactionResult(documentId, "tmp:block:1", UUID.randomUUID(), 1, "000000000001000000000000", null));

        mockMvc.perform(post("/v1/admin/documents/{documentId}/blocks", documentId)
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "admin-api",
                                  "batchId": "batch-1",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value("OK"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.data.batchId").value("batch-1"))
                .andExpect(jsonPath("$.data.appliedOperations[0].opId").value("op-1"))
                .andExpect(jsonPath("$.data.appliedOperations[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.appliedOperations[0].tempId").value("tmp:block:1"));

        verify(adminBlockTransactionService).applyCreate(eq(documentId), any(), any(), eq("user-123"));
    }

    @Test
    @DisplayName("성공_수정 API는 blockId로 문서 ID를 찾아 같은 transaction 경로를 호출한다")
    void updateBlockDelegatesSingleReplaceContentTransaction() throws Exception {
        UUID blockId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(adminBlockTransactionService.applyReplaceContent(eq(blockId), any(), any(), eq("user-456")))
                .thenReturn(transactionResult(documentId, null, blockId, 1, "000000000001000000000000", null));

        mockMvc.perform(patch("/v1/admin/blocks/{blockId}", blockId)
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "admin-api",
                                  "batchId": "batch-2",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "content": {
                                        "format": "rich_text",
                                        "schemaVersion": 1,
                                        "segments": [
                                          {
                                            "text": "수정된 블록",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """.formatted(blockId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.data.appliedOperations[0].blockId").value(blockId.toString()))
                .andExpect(jsonPath("$.data.appliedOperations[0].version").value(1));

        verify(adminBlockTransactionService).applyReplaceContent(eq(blockId), any(), any(), eq("user-456"));
    }

    @Test
    @DisplayName("성공_삭제 API는 transaction delete 응답을 반환한다")
    void deleteBlockDelegatesSingleDeleteTransaction() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        LocalDateTime deletedAt = LocalDateTime.of(2026, 3, 25, 12, 0);
        when(adminBlockTransactionService.applyDelete(eq(blockId), any(), any(), eq("user-123")))
                .thenReturn(transactionResult(documentId, null, blockId, null, null, deletedAt));

        mockMvc.perform(delete("/v1/admin/blocks/{blockId}", blockId)
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "admin-api",
                                  "batchId": "batch-3",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "%s",
                                      "version": 3
                                    }
                                  ]
                                }
                                """.formatted(blockId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].blockId").value(blockId.toString()))
                .andExpect(jsonPath("$.data.appliedOperations[0].deletedAt").exists());

        verify(adminBlockTransactionService).applyDelete(eq(blockId), any(), any(), eq("user-123"));
    }

    @Test
    @DisplayName("성공_이동 API는 transaction move 응답을 반환한다")
    void moveBlockDelegatesSingleMoveTransaction() throws Exception {
        UUID blockId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(adminBlockTransactionService.applyMove(eq(blockId), any(), any(), eq("user-123")))
                .thenReturn(transactionResult(documentId, null, blockId, 2, "000000000002000000000000", null));

        mockMvc.perform(post("/v1/admin/blocks/{blockId}/move", blockId)
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "admin-api",
                                  "batchId": "batch-4",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 1,
                                      "parentRef": null,
                                      "afterRef": null,
                                      "beforeRef": null
                                    }
                                  ]
                                }
                                """.formatted(blockId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.appliedOperations[0].sortKey").value("000000000002000000000000"));

        verify(adminBlockTransactionService).applyMove(eq(blockId), any(), any(), eq("user-123"));
    }

    @Test
    @DisplayName("실패_단일 operation이 아니면 잘못된 요청을 반환한다")
    void updateBlockRejectsMultipleOperations() throws Exception {
        UUID blockId = UUID.randomUUID();
        when(adminBlockTransactionService.applyReplaceContent(eq(blockId), any(), any(), eq("user-123")))
                .thenThrow(new BusinessException(BusinessErrorCode.INVALID_REQUEST));

        var result = mockMvc.perform(patch("/v1/admin/blocks/{blockId}", blockId)
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "admin-api",
                                  "batchId": "batch-5",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "content": {
                                        "format": "rich_text",
                                        "schemaVersion": 1,
                                        "segments": [
                                          {
                                            "text": "A",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "%s",
                                      "version": 1,
                                      "content": {
                                        "format": "rich_text",
                                        "schemaVersion": 1,
                                        "segments": [
                                          {
                                            "text": "B",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """.formatted(blockId, blockId)));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9015, "잘못된 요청입니다.");
    }

    @Test
    @DisplayName("실패_path blockId와 blockRef가 다르면 잘못된 요청을 반환한다")
    void updateBlockRejectsMismatchedBlockReference() throws Exception {
        UUID blockId = UUID.randomUUID();
        when(adminBlockTransactionService.applyReplaceContent(eq(blockId), any(), any(), eq("user-123")))
                .thenThrow(new BusinessException(BusinessErrorCode.INVALID_REQUEST));

        var result = mockMvc.perform(patch("/v1/admin/blocks/{blockId}", blockId)
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "admin-api",
                                  "batchId": "batch-6",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "content": {
                                        "format": "rich_text",
                                        "schemaVersion": 1,
                                        "segments": [
                                          {
                                            "text": "수정",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """.formatted(UUID.randomUUID())));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9015, "잘못된 요청입니다.");
    }

    @Test
    @DisplayName("실패_block 조회 실패는 그대로 전파한다")
    void deleteBlockPropagatesBlockNotFound() throws Exception {
        UUID blockId = UUID.randomUUID();
        when(adminBlockTransactionService.applyDelete(eq(blockId), any(), any(), eq("user-123")))
                .thenThrow(new BusinessException(BusinessErrorCode.BLOCK_NOT_FOUND));

        var result = mockMvc.perform(delete("/v1/admin/blocks/{blockId}", blockId)
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "admin-api",
                                  "batchId": "batch-7",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "%s",
                                      "version": 0
                                    }
                                  ]
                                }
                                """.formatted(blockId)));

        ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9006, "요청한 블록을 찾을 수 없습니다.");
    }

    private DocumentTransactionResult transactionResult(
            UUID documentId,
            String tempId,
            UUID blockId,
            Integer version,
            String sortKey,
            LocalDateTime deletedAt
    ) {
        return new DocumentTransactionResult(
                documentId,
                1,
                "batch-1",
                List.of(new DocumentTransactionAppliedOperationResult(
                        "op-1",
                        DocumentTransactionOperationStatus.APPLIED,
                        tempId,
                        blockId,
                        version,
                        sortKey,
                        deletedAt
                ))
        );
    }
}
