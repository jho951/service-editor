package com.documents.api.editor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.documents.api.auth.CurrentUserIdArgumentResolver;
import com.documents.api.block.support.BlockJsonCodec;
import com.documents.api.exception.GlobalExceptionHandler;
import com.documents.api.support.ApiResponseAssertions;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.service.EditorOperationOrchestrator;
import com.documents.service.editor.EditorMoveResourceType;
import com.documents.service.editor.EditorMoveResult;
import com.documents.service.editor.EditorSaveAppliedOperationResult;
import com.documents.service.editor.EditorSaveOperationStatus;
import com.documents.service.editor.EditorSaveResult;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("EditorOperation 컨트롤러 빠른 검증")
class EditorOperationControllerWebMvcTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ACTOR_ID = "user-123";

    @Mock
    private EditorOperationOrchestrator editorOperationOrchestrator;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        BlockJsonCodec blockJsonCodec = new BlockJsonCodec(new ObjectMapper());

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new EditorOperationController(
                                editorOperationOrchestrator,
                                new EditorSaveApiMapper(blockJsonCodec),
                                new EditorMoveApiMapper()
                        )
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("성공_save 요청은 editor save 응답을 그대로 반환한다")
    void saveDelegatesToEditorOperationOrchestrator() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(editorOperationOrchestrator.save(eq(documentId), any(), eq(ACTOR_ID)))
                .thenReturn(new EditorSaveResult(
                        documentId,
                        4L,
                        "batch-1",
                        List.of(new EditorSaveAppliedOperationResult(
                                "op-1",
                                EditorSaveOperationStatus.APPLIED,
                                "tmp:block:1",
                                UUID.randomUUID(),
                                1L,
                                "000000000001000000000000",
                                null
                        ))
                ));

        mockMvc.perform(post("/editor-operations/documents/{documentId}/save", documentId)
                        .contentType("application/json")
                        .header(USER_ID_HEADER, ACTOR_ID)
                        .content("""
                                {
                                  "clientId": "editor",
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
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.data.documentVersion").value(4))
                .andExpect(jsonPath("$.data.batchId").value("batch-1"))
                .andExpect(jsonPath("$.data.appliedOperations[0].opId").value("op-1"))
                .andExpect(jsonPath("$.data.appliedOperations[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.appliedOperations[0].tempId").value("tmp:block:1"));
    }

    @Test
    @DisplayName("성공_block_delete는 version 없이도 request shape 검증을 통과한다")
    void saveAllowsBlockDeleteWithoutVersion() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        when(editorOperationOrchestrator.save(eq(documentId), any(), eq(ACTOR_ID)))
                .thenReturn(new EditorSaveResult(
                        documentId,
                        1L,
                        "batch-delete-no-version",
                        List.of(new EditorSaveAppliedOperationResult(
                                "op-1",
                                EditorSaveOperationStatus.APPLIED,
                                null,
                                blockId,
                                null,
                                null,
                                null
                        ))
                ));

        mockMvc.perform(post("/editor-operations/documents/{documentId}/save", documentId)
                        .contentType("application/json")
                        .header(USER_ID_HEADER, ACTOR_ID)
                        .content("""
                                {
                                  "clientId": "editor",
                                  "batchId": "batch-delete-no-version",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "%s"
                                    }
                                  ]
                                }
                                """.formatted(blockId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentVersion").value(1))
                .andExpect(jsonPath("$.data.appliedOperations[0].blockId").value(blockId.toString()));
    }

    @Test
    @DisplayName("성공_block_move는 version 없이도 request shape 검증을 통과한다")
    void saveAllowsBlockMoveWithoutVersion() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        when(editorOperationOrchestrator.save(eq(documentId), any(), eq(ACTOR_ID)))
                .thenReturn(new EditorSaveResult(
                        documentId,
                        1L,
                        "batch-move-no-version",
                        List.of(new EditorSaveAppliedOperationResult(
                                "op-1",
                                EditorSaveOperationStatus.APPLIED,
                                null,
                                blockId,
                                1L,
                                "000000000001I00000000000",
                                null
                        ))
                ));

        mockMvc.perform(post("/editor-operations/documents/{documentId}/save", documentId)
                        .contentType("application/json")
                        .header(USER_ID_HEADER, ACTOR_ID)
                        .content("""
                                {
                                  "clientId": "editor",
                                  "batchId": "batch-move-no-version",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "parentRef": null,
                                      "afterRef": null,
                                      "beforeRef": null
                                    }
                                  ]
                                }
                                """.formatted(blockId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentVersion").value(1))
                .andExpect(jsonPath("$.data.appliedOperations[0].blockId").value(blockId.toString()))
                .andExpect(jsonPath("$.data.appliedOperations[0].version").value(1));
    }

    @Test
    @DisplayName("실패_save 서비스가 잘못된 요청 예외를 던지면 잘못된 요청 응답을 반환한다")
    void saveReturnsBadRequestWhenServiceRejectsRequest() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(editorOperationOrchestrator.save(eq(documentId), any(), eq(ACTOR_ID)))
                .thenThrow(new BusinessException(BusinessErrorCode.INVALID_REQUEST));

        var result = mockMvc.perform(post("/editor-operations/documents/{documentId}/save", documentId)
                .contentType("application/json")
                .header(USER_ID_HEADER, ACTOR_ID)
                .content("""
                        {
                          "clientId": "editor",
                          "batchId": "batch-1",
                          "operations": [
                            {
                              "opId": "op-1",
                              "type": "BLOCK_REPLACE_CONTENT",
                              "blockRef": "not-a-uuid",
                              "content": {
                                "format": "rich_text",
                                "schemaVersion": 1,
                                "segments": [
                                  {
                                    "text": "실패",
                                    "marks": []
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9015, "잘못된 요청입니다.");
    }

    @Test
    @DisplayName("실패_save 서비스가 충돌 예외를 던지면 충돌 응답을 반환한다")
    void saveReturnsConflictWhenServiceRejectsWithConflict() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(editorOperationOrchestrator.save(eq(documentId), any(), eq(ACTOR_ID)))
                .thenThrow(new BusinessException(BusinessErrorCode.CONFLICT));

        var result = mockMvc.perform(post("/editor-operations/documents/{documentId}/save", documentId)
                .contentType("application/json")
                .header(USER_ID_HEADER, ACTOR_ID)
                .content("""
                        {
                          "clientId": "editor",
                          "batchId": "batch-1",
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
                                    "text": "충돌",
                                    "marks": []
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """.formatted(UUID.randomUUID())));

        ApiResponseAssertions.assertErrorEnvelope(result, "CONFLICT", 9005, "요청이 현재 리소스 상태와 충돌합니다.");
    }

    @Test
    @DisplayName("성공_save 응답은 block move no-op status를 그대로 반환한다")
    void saveReturnsNoOpStatusForBlockMove() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        when(editorOperationOrchestrator.save(eq(documentId), any(), eq(ACTOR_ID)))
                .thenReturn(new EditorSaveResult(
                        documentId,
                        0L,
                        "batch-no-op",
                        List.of(new EditorSaveAppliedOperationResult(
                                "op-1",
                                EditorSaveOperationStatus.NO_OP,
                                null,
                                blockId,
                                4L,
                                "000000000001000000000000",
                                null
                        ))
                ));

        mockMvc.perform(post("/editor-operations/documents/{documentId}/save", documentId)
                        .contentType("application/json")
                        .header(USER_ID_HEADER, ACTOR_ID)
                        .content("""
                                {
                                  "clientId": "editor",
                                  "batchId": "batch-no-op",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 4
                                    }
                                  ]
                                }
                                """.formatted(blockId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentVersion").value(0))
                .andExpect(jsonPath("$.data.appliedOperations[0].status").value("NO_OP"))
                .andExpect(jsonPath("$.data.appliedOperations[0].version").value(4));
    }

    @Test
    @DisplayName("성공_save 응답은 replace_content no-op status를 그대로 반환한다")
    void saveReturnsNoOpStatusForReplaceContent() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        when(editorOperationOrchestrator.save(eq(documentId), any(), eq(ACTOR_ID)))
                .thenReturn(new EditorSaveResult(
                        documentId,
                        0L,
                        "batch-no-op-replace",
                        List.of(new EditorSaveAppliedOperationResult(
                                "op-1",
                                EditorSaveOperationStatus.NO_OP,
                                null,
                                blockId,
                                3L,
                                "000000000001000000000000",
                                null
                        ))
                ));

        mockMvc.perform(post("/editor-operations/documents/{documentId}/save", documentId)
                        .contentType("application/json")
                        .header(USER_ID_HEADER, ACTOR_ID)
                        .content("""
                                {
                                  "clientId": "editor",
                                  "batchId": "batch-no-op-replace",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "%s",
                                      "version": 3,
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
                .andExpect(jsonPath("$.data.documentVersion").value(0))
                .andExpect(jsonPath("$.data.appliedOperations[0].status").value("NO_OP"))
                .andExpect(jsonPath("$.data.appliedOperations[0].version").value(3));
    }

    @Test
    @DisplayName("실패_save 요청에 인증 헤더가 없으면 인증 오류 응답을 반환한다")
    void saveReturnsUnauthorizedWhenHeaderMissing() throws Exception {
        UUID documentId = UUID.randomUUID();

        var result = mockMvc.perform(post("/editor-operations/documents/{documentId}/save", documentId)
                .contentType("application/json")
                .content("""
                        {
                          "clientId": "editor",
                          "batchId": "batch-1",
                          "operations": [
                            {
                              "opId": "op-1",
                              "type": "BLOCK_CREATE",
                              "blockRef": "tmp:block:1"
                            }
                          ]
                        }
                        """));

        ApiResponseAssertions.assertErrorEnvelope(result, "UNAUTHORIZED", 9001, "인증 정보가 없습니다.");
        verify(editorOperationOrchestrator, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("실패_replace_content에 content가 없으면 유효성 검사 오류를 반환한다")
    void saveRejectsReplaceContentWithoutContent() throws Exception {
        var result = mockMvc.perform(post("/editor-operations/documents/{documentId}/save", UUID.randomUUID())
                .contentType("application/json")
                .header(USER_ID_HEADER, ACTOR_ID)
                .content("""
                        {
                          "clientId": "editor",
                          "batchId": "batch-1",
                          "operations": [
                            {
                              "opId": "op-1",
                              "type": "BLOCK_REPLACE_CONTENT",
                              "blockRef": "tmp:block:1"
                            }
                          ]
                        }
                        """));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
        verify(editorOperationOrchestrator, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("실패_replace_content에 content가 null이면 유효성 검사 오류를 반환한다")
    void saveRejectsReplaceContentWithNullContent() throws Exception {
        var result = mockMvc.perform(post("/editor-operations/documents/{documentId}/save", UUID.randomUUID())
                .contentType("application/json")
                .header(USER_ID_HEADER, ACTOR_ID)
                .content("""
                        {
                          "clientId": "editor",
                          "batchId": "batch-1",
                          "operations": [
                            {
                              "opId": "op-1",
                              "type": "BLOCK_REPLACE_CONTENT",
                              "blockRef": "tmp:block:1",
                              "content": null
                            }
                          ]
                        }
                        """));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
        verify(editorOperationOrchestrator, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("실패_create에 content 스키마가 맞지 않으면 유효성 검사 오류를 반환한다")
    void saveRejectsCreateWithInvalidContentSchema() throws Exception {
        var result = mockMvc.perform(post("/editor-operations/documents/{documentId}/save", UUID.randomUUID())
                .contentType("application/json")
                .header(USER_ID_HEADER, ACTOR_ID)
                .content("""
                        {
                          "clientId": "editor",
                          "batchId": "batch-1",
                          "operations": [
                            {
                              "opId": "op-1",
                              "type": "BLOCK_CREATE",
                              "blockRef": "tmp:block:1",
                              "content": {
                                "format": "rich_text",
                                "schemaVersion": 2,
                                "segments": [
                                  {
                                    "text": "잘못된 create",
                                    "marks": []
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
        verify(editorOperationOrchestrator, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("실패_create에 content format이 맞지 않으면 유효성 검사 오류를 반환한다")
    void saveRejectsCreateWithInvalidContentFormat() throws Exception {
        var result = mockMvc.perform(post("/editor-operations/documents/{documentId}/save", UUID.randomUUID())
                .contentType("application/json")
                .header(USER_ID_HEADER, ACTOR_ID)
                .content("""
                        {
                          "clientId": "editor",
                          "batchId": "batch-1",
                          "operations": [
                            {
                              "opId": "op-1",
                              "type": "BLOCK_CREATE",
                              "blockRef": "tmp:block:1",
                              "content": {
                                "format": "plain_text",
                                "schemaVersion": 1,
                                "segments": [
                                  {
                                    "text": "잘못된 format",
                                    "marks": []
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
        verify(editorOperationOrchestrator, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("실패_create에 content segments가 비어 있으면 유효성 검사 오류를 반환한다")
    void saveRejectsCreateWithEmptySegments() throws Exception {
        var result = mockMvc.perform(post("/editor-operations/documents/{documentId}/save", UUID.randomUUID())
                .contentType("application/json")
                .header(USER_ID_HEADER, ACTOR_ID)
                .content("""
                        {
                          "clientId": "editor",
                          "batchId": "batch-1",
                          "operations": [
                            {
                              "opId": "op-1",
                              "type": "BLOCK_CREATE",
                              "blockRef": "tmp:block:1",
                              "content": {
                                "format": "rich_text",
                                "schemaVersion": 1,
                                "segments": []
                              }
                            }
                          ]
                        }
                        """));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
        verify(editorOperationOrchestrator, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("실패_create에 content textColor 값이 잘못되면 유효성 검사 오류를 반환한다")
    void saveRejectsCreateWithInvalidTextColorMark() throws Exception {
        var result = mockMvc.perform(post("/editor-operations/documents/{documentId}/save", UUID.randomUUID())
                .contentType("application/json")
                .header(USER_ID_HEADER, ACTOR_ID)
                .content("""
                        {
                          "clientId": "editor",
                          "batchId": "batch-1",
                          "operations": [
                            {
                              "opId": "op-1",
                              "type": "BLOCK_CREATE",
                              "blockRef": "tmp:block:1",
                              "content": {
                                "format": "rich_text",
                                "schemaVersion": 1,
                                "segments": [
                                  {
                                    "text": "잘못된 색상",
                                    "marks": [
                                      {
                                        "type": "textColor",
                                        "value": "red"
                                      }
                                    ]
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
        verify(editorOperationOrchestrator, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("실패_create에 version이 함께 오면 유효성 검사 오류를 반환한다")
    void saveRejectsCreateWithVersion() throws Exception {
        var result = mockMvc.perform(post("/editor-operations/documents/{documentId}/save", UUID.randomUUID())
                .contentType("application/json")
                .header(USER_ID_HEADER, ACTOR_ID)
                .content("""
                        {
                          "clientId": "editor",
                          "batchId": "batch-1",
                          "operations": [
                            {
                              "opId": "op-1",
                              "type": "BLOCK_CREATE",
                              "blockRef": "tmp:block:1",
                              "version": 0
                            }
                          ]
                        }
                        """));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
        verify(editorOperationOrchestrator, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("실패_create에 blockRef가 없으면 유효성 검사 오류를 반환한다")
    void saveRejectsCreateWithoutBlockRef() throws Exception {
        var result = mockMvc.perform(post("/editor-operations/documents/{documentId}/save", UUID.randomUUID())
                .contentType("application/json")
                .header(USER_ID_HEADER, ACTOR_ID)
                .content("""
                        {
                          "clientId": "editor",
                          "batchId": "batch-1",
                          "operations": [
                            {
                              "opId": "op-1",
                              "type": "BLOCK_CREATE"
                            }
                          ]
                        }
                        """));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
        verify(editorOperationOrchestrator, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("실패_replace_content에 위치 필드가 함께 오면 유효성 검사 오류를 반환한다")
    void saveRejectsReplaceContentWithPositionFields() throws Exception {
        var result = mockMvc.perform(post("/editor-operations/documents/{documentId}/save", UUID.randomUUID())
                .contentType("application/json")
                .header(USER_ID_HEADER, ACTOR_ID)
                .content("""
                        {
                          "clientId": "editor",
                          "batchId": "batch-1",
                          "operations": [
                            {
                              "opId": "op-1",
                              "type": "BLOCK_REPLACE_CONTENT",
                              "blockRef": "tmp:block:1",
                              "parentRef": "%s",
                              "content": {
                                "format": "rich_text",
                                "schemaVersion": 1,
                                "segments": [
                                  {
                                    "text": "잘못된 replace",
                                    "marks": []
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """.formatted(UUID.randomUUID())));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
        verify(editorOperationOrchestrator, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("실패_block_move에 content가 함께 오면 유효성 검사 오류를 반환한다")
    void saveRejectsBlockMoveWithContent() throws Exception {
        var result = mockMvc.perform(post("/editor-operations/documents/{documentId}/save", UUID.randomUUID())
                .contentType("application/json")
                .header(USER_ID_HEADER, ACTOR_ID)
                .content("""
                        {
                          "clientId": "editor",
                          "batchId": "batch-1",
                          "operations": [
                            {
                              "opId": "op-1",
                              "type": "BLOCK_MOVE",
                              "blockRef": "%s",
                              "version": 0,
                              "content": {
                                "format": "rich_text",
                                "schemaVersion": 1,
                                "segments": [
                                  {
                                    "text": "잘못된 move",
                                    "marks": []
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """.formatted(UUID.randomUUID())));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
        verify(editorOperationOrchestrator, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("성공_document move 요청은 orchestrator 응답을 반환한다")
    void moveDocumentReturnsOrchestratorResponse() throws Exception {
        UUID resourceId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        when(editorOperationOrchestrator.move(any(), eq(ACTOR_ID)))
                .thenReturn(new EditorMoveResult(
                        EditorMoveResourceType.DOCUMENT,
                        resourceId,
                        parentId,
                        4L,
                        4L,
                        "000000000002000000000000"
                ));

        mockMvc.perform(post("/editor-operations/move")
                        .contentType("application/json")
                        .header(USER_ID_HEADER, ACTOR_ID)
                        .content("""
                                {
                                  "resourceType": "DOCUMENT",
                                  "resourceId": "%s",
                                  "targetParentId": "%s",
                                  "afterId": null,
                                  "beforeId": null
                                }
                                """.formatted(resourceId, parentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value("OK"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("요청 응답 성공"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.resourceType").value("DOCUMENT"))
                .andExpect(jsonPath("$.data.resourceId").value(resourceId.toString()))
                .andExpect(jsonPath("$.data.parentId").value(parentId.toString()))
                .andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.documentVersion").value(4))
                .andExpect(jsonPath("$.data.sortKey").value("000000000002000000000000"));

        verify(editorOperationOrchestrator).move(any(), eq(ACTOR_ID));
    }

    @Test
    @DisplayName("성공_block move 요청은 orchestrator 응답을 반환한다")
    void moveBlockReturnsOrchestratorResponse() throws Exception {
        UUID resourceId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        when(editorOperationOrchestrator.move(any(), eq(ACTOR_ID)))
                .thenReturn(new EditorMoveResult(
                        EditorMoveResourceType.BLOCK,
                        resourceId,
                        parentId,
                        3L,
                        8L,
                        "000000000003000000000000"
                ));

        mockMvc.perform(post("/editor-operations/move")
                        .contentType("application/json")
                        .header(USER_ID_HEADER, ACTOR_ID)
                        .content("""
                                {
                                  "resourceType": "BLOCK",
                                  "resourceId": "%s",
                                  "targetParentId": "%s",
                                  "afterId": null,
                                  "beforeId": null,
                                  "version": 3
                                }
                                """.formatted(resourceId, parentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resourceType").value("BLOCK"))
                .andExpect(jsonPath("$.data.resourceId").value(resourceId.toString()))
                .andExpect(jsonPath("$.data.parentId").value(parentId.toString()))
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.documentVersion").value(8))
                .andExpect(jsonPath("$.data.sortKey").value("000000000003000000000000"));

        verify(editorOperationOrchestrator).move(any(), eq(ACTOR_ID));
    }

    @Test
    @DisplayName("실패_block move 요청에 version이 없으면 유효성 검사 오류를 반환한다")
    void moveBlockRejectsMissingVersion() throws Exception {
        UUID resourceId = UUID.randomUUID();

        var result = mockMvc.perform(post("/editor-operations/move")
                .contentType("application/json")
                .header(USER_ID_HEADER, ACTOR_ID)
                .content("""
                        {
                          "resourceType": "BLOCK",
                          "resourceId": "%s",
                          "targetParentId": null,
                          "afterId": null,
                          "beforeId": null
                        }
                        """.formatted(resourceId)));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
        verify(editorOperationOrchestrator, never()).move(any(), any());
    }

    @Test
    @DisplayName("실패_document move 서비스가 문서 없음 예외를 던지면 not found를 반환한다")
    void moveDocumentReturnsNotFoundWhenDocumentMissing() throws Exception {
        UUID resourceId = UUID.randomUUID();
        doThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND))
                .when(editorOperationOrchestrator).move(any(), eq(ACTOR_ID));

        var result = mockMvc.perform(post("/editor-operations/move")
                .contentType("application/json")
                .header(USER_ID_HEADER, ACTOR_ID)
                .content("""
                        {
                          "resourceType": "DOCUMENT",
                          "resourceId": "%s",
                          "targetParentId": null,
                          "afterId": null,
                          "beforeId": null
                        }
                        """.formatted(resourceId)));

        ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("실패_document move 서비스가 잘못된 요청 예외를 던지면 bad request를 반환한다")
    void moveDocumentReturnsBadRequestWhenParentIsSelf() throws Exception {
        UUID resourceId = UUID.randomUUID();
        doThrow(new BusinessException(BusinessErrorCode.INVALID_REQUEST))
                .when(editorOperationOrchestrator).move(any(), eq(ACTOR_ID));

        var result = mockMvc.perform(post("/editor-operations/move")
                .contentType("application/json")
                .header(USER_ID_HEADER, ACTOR_ID)
                .content("""
                        {
                          "resourceType": "DOCUMENT",
                          "resourceId": "%s",
                          "targetParentId": "%s",
                          "afterId": null,
                          "beforeId": null
                        }
                        """.formatted(resourceId, resourceId)));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9015, "잘못된 요청입니다.");
    }

    @Test
    @DisplayName("실패_block move 서비스가 충돌 예외를 던지면 conflict를 반환한다")
    void moveBlockReturnsConflictWhenVersionStale() throws Exception {
        UUID resourceId = UUID.randomUUID();
        doThrow(new BusinessException(BusinessErrorCode.CONFLICT))
                .when(editorOperationOrchestrator).move(any(), eq(ACTOR_ID));

        var result = mockMvc.perform(post("/editor-operations/move")
                .contentType("application/json")
                .header(USER_ID_HEADER, ACTOR_ID)
                .content("""
                        {
                          "resourceType": "BLOCK",
                          "resourceId": "%s",
                          "targetParentId": null,
                          "afterId": null,
                          "beforeId": null,
                          "version": 1
                        }
                        """.formatted(resourceId)));

        ApiResponseAssertions.assertErrorEnvelope(result, "CONFLICT", 9005, "요청이 현재 리소스 상태와 충돌합니다.");
    }

    @Test
    @DisplayName("실패_사용자 식별자 헤더 없이 move 요청하면 인증 오류 응답을 반환한다")
    void moveReturnsUnauthorizedWhenHeaderMissing() throws Exception {
        UUID resourceId = UUID.randomUUID();

        var result = mockMvc.perform(post("/editor-operations/move")
                .contentType("application/json")
                .content("""
                        {
                          "resourceType": "DOCUMENT",
                          "resourceId": "%s",
                          "targetParentId": null,
                          "afterId": null,
                          "beforeId": null
                        }
                        """.formatted(resourceId)));

        ApiResponseAssertions.assertErrorEnvelope(result, "UNAUTHORIZED", 9001, "인증 정보가 없습니다.");
        verify(editorOperationOrchestrator, never()).move(any(), any());
    }
}
