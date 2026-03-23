package com.documents.api.document;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

import com.documents.api.block.support.BlockJsonCodec;
import com.documents.api.document.support.DocumentJsonCodec;
import com.documents.api.exception.GlobalExceptionHandler;
import com.documents.api.support.ApiResponseAssertions;
import com.documents.domain.Document;
import com.documents.domain.Workspace;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.service.DocumentService;
import com.documents.service.DocumentTransactionService;
import com.documents.service.transaction.DocumentTransactionAppliedOperationResult;
import com.documents.service.transaction.DocumentTransactionOperationStatus;
import com.documents.service.transaction.DocumentTransactionResult;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("Document 컨트롤러 빠른 검증")
class DocumentControllerWebMvcTest {

	private static final String USER_ID_HEADER = "X-User-Id";
	private static final String ACTOR_ID = "user-123";
	private static final String ROOT_WORKSPACE_NAME = "Docs Root";
	private static final String PROJECT_OVERVIEW_TITLE = "프로젝트 개요";
	private static final String UPDATED_PROJECT_OVERVIEW_TITLE = "수정된 프로젝트 개요";
	private static final String ROOT_DOCUMENT_TITLE = "루트 문서";
	private static final String CHILD_DOCUMENT_TITLE = "하위 문서";
	private static final String PARENT_DOCUMENT_TITLE = "부모 문서";
	private static final String ICON_DOC_JSON = "{\"type\":\"emoji\",\"value\":\"📄\"}";
	private static final String COVER_1_JSON = "{\"type\":\"image\",\"value\":\"cover-1\"}";
	private static final String COVER_2_JSON = "{\"type\":\"image\",\"value\":\"cover-2\"}";
	private static final LocalDateTime FIXTURE_TIME = LocalDateTime.of(2026, 3, 16, 0, 0);

	@Mock
	private DocumentService documentService;

	@Mock
	private DocumentTransactionService documentTransactionService;

	private MockMvc mockMvc;

	private Document document(
		UUID id,
		UUID workspaceId,
		UUID parentId,
		String title,
		String actorId,
		Integer version,
		String sortKey,
		String iconJson,
		String coverJson
	) {
		Document document = Document.builder()
			.id(id)
			.workspace(workspace(workspaceId))
			.parent(parentId == null ? null : parentDocument(parentId, workspaceId))
			.title(title)
			.sortKey(sortKey)
			.iconJson(iconJson)
			.coverJson(coverJson)
			.createdBy(actorId)
			.updatedBy(actorId)
			.build();
		document.setCreatedAt(FIXTURE_TIME);
		document.setUpdatedAt(FIXTURE_TIME);
		document.setVersion(version);
		return document;
	}

	private Workspace workspace(UUID workspaceId) {
		return Workspace.builder()
			.id(workspaceId)
			.name(ROOT_WORKSPACE_NAME)
			.build();
	}

	private Document parentDocument(UUID documentId, UUID workspaceId) {
		return Document.builder()
			.id(documentId)
			.workspace(workspace(workspaceId))
			.title(PARENT_DOCUMENT_TITLE)
			.sortKey("00000000000000000001")
			.build();
	}

	@BeforeEach
	void setUp() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();

			mockMvc = MockMvcBuilders.standaloneSetup(new DocumentController(
					documentService,
					new DocumentApiMapper(new DocumentJsonCodec(new ObjectMapper())),
					documentTransactionService,
					new DocumentTransactionApiMapper(new BlockJsonCodec(new ObjectMapper()))
				))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setValidator(validator)
				.build();
		}

	@Test
	@DisplayName("성공_create와 replace_content transaction 요청에 대해 매핑 응답을 반환한다")
	void applyTransactionsReturnsAppliedOperations() throws Exception {
		UUID documentId = UUID.randomUUID();
		UUID blockId = UUID.randomUUID();

		when(documentTransactionService.apply(eq(documentId), any(), eq(ACTOR_ID)))
			.thenReturn(new DocumentTransactionResult(
				documentId,
				1,
				"batch-1",
				List.of(
					new DocumentTransactionAppliedOperationResult(
						"op-1",
						DocumentTransactionOperationStatus.APPLIED,
						"tmp:block:1",
						blockId,
						0,
						"000000000001000000000000",
						null
					),
					new DocumentTransactionAppliedOperationResult(
						"op-2",
						DocumentTransactionOperationStatus.APPLIED,
						null,
						blockId,
						1,
						"000000000001000000000000",
						null
					)
				)
			));

		mockMvc.perform(post("/v1/documents/{documentId}/transactions", documentId)
				.contentType("application/json")
				.header(USER_ID_HEADER, ACTOR_ID)
				.content("""
					{
					  "clientId": "web-editor",
                      "documentVersion": 0,
					  "batchId": "batch-1",
					  "operations": [
						    {
						      "opId": "op-1",
						      "type": "BLOCK_CREATE",
						      "blockRef": "tmp:block:1",
						      "parentRef": null,
						      "afterRef": null,
						      "beforeRef": null
					    },
					    {
					      "opId": "op-2",
					      "type": "BLOCK_REPLACE_CONTENT",
						      "blockRef": "tmp:block:1",
					      "content": {
					        "format": "rich_text",
					        "schemaVersion": 1,
					        "segments": [
					          {
					            "text": "새 블록",
					            "marks": []
					          }
					        ]
					      }
					    }
					  ]
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.documentId").value(documentId.toString()))
			.andExpect(jsonPath("$.data.documentVersion").value(1))
			.andExpect(jsonPath("$.data.batchId").value("batch-1"))
			.andExpect(jsonPath("$.data.appliedOperations[0].opId").value("op-1"))
			.andExpect(jsonPath("$.data.appliedOperations[0].tempId").value("tmp:block:1"))
			.andExpect(jsonPath("$.data.appliedOperations[0].blockId").value(blockId.toString()))
			.andExpect(jsonPath("$.data.appliedOperations[0].version").value(0))
			.andExpect(jsonPath("$.data.appliedOperations[1].opId").value("op-2"))
			.andExpect(jsonPath("$.data.appliedOperations[1].status").value("APPLIED"))
			.andExpect(jsonPath("$.data.appliedOperations[1].blockId").value(blockId.toString()))
			.andExpect(jsonPath("$.data.appliedOperations[1].version").value(1));
	}

	@Test
	@DisplayName("성공_block_delete transaction 요청에 대해 삭제 시각을 포함한 적용 응답을 반환한다")
	void applyTransactionsReturnsAppliedOperationsForBlockDelete() throws Exception {
		UUID documentId = UUID.randomUUID();
		UUID blockId = UUID.randomUUID();
		LocalDateTime deletedAt = LocalDateTime.of(2026, 3, 22, 22, 0);

		when(documentTransactionService.apply(eq(documentId), any(), eq(ACTOR_ID)))
			.thenReturn(new DocumentTransactionResult(
				documentId,
				1,
				"batch-delete",
				List.of(
					new DocumentTransactionAppliedOperationResult(
						"op-1",
						DocumentTransactionOperationStatus.APPLIED,
						null,
						blockId,
						null,
						null,
						deletedAt
					)
				)
			));

		mockMvc.perform(post("/v1/documents/{documentId}/transactions", documentId)
				.contentType("application/json")
				.header(USER_ID_HEADER, ACTOR_ID)
				.content("""
					{
					  "clientId": "web-editor",
                      "documentVersion": 0,
					  "batchId": "batch-delete",
					  "operations": [
					    {
					      "opId": "op-1",
					      "type": "BLOCK_DELETE",
					      "blockRef": "%s",
					      "version": 4
					    }
					  ]
					}
					""".formatted(blockId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.documentId").value(documentId.toString()))
			.andExpect(jsonPath("$.data.documentVersion").value(1))
			.andExpect(jsonPath("$.data.batchId").value("batch-delete"))
			.andExpect(jsonPath("$.data.appliedOperations[0].opId").value("op-1"))
			.andExpect(jsonPath("$.data.appliedOperations[0].blockId").value(blockId.toString()))
			.andExpect(jsonPath("$.data.appliedOperations[0].deletedAt").exists());
	}

	@Test
	@DisplayName("성공_block_move transaction 요청에 대해 version과 sortKey 응답을 반환한다")
	void applyTransactionsReturnsAppliedOperationsForBlockMove() throws Exception {
		UUID documentId = UUID.randomUUID();
		UUID blockId = UUID.randomUUID();

		when(documentTransactionService.apply(eq(documentId), any(), eq(ACTOR_ID)))
			.thenReturn(new DocumentTransactionResult(
				documentId,
				1,
				"batch-move",
				List.of(
					new DocumentTransactionAppliedOperationResult(
						"op-1",
						DocumentTransactionOperationStatus.APPLIED,
						null,
						blockId,
						5,
						"000000000001I00000000000",
						null
					)
				)
			));

		mockMvc.perform(post("/v1/documents/{documentId}/transactions", documentId)
				.contentType("application/json")
				.header(USER_ID_HEADER, ACTOR_ID)
				.content("""
					{
					  "clientId": "web-editor",
                      "documentVersion": 0,
					  "batchId": "batch-move",
					  "operations": [
					    {
					      "opId": "op-1",
					      "type": "BLOCK_MOVE",
					      "blockRef": "%s",
					      "version": 4,
					      "parentRef": null,
					      "afterRef": "%s",
					      "beforeRef": null
					    }
					  ]
					}
					""".formatted(blockId, UUID.randomUUID())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.documentId").value(documentId.toString()))
			.andExpect(jsonPath("$.data.documentVersion").value(1))
			.andExpect(jsonPath("$.data.appliedOperations[0].blockId").value(blockId.toString()))
			.andExpect(jsonPath("$.data.appliedOperations[0].version").value(5))
			.andExpect(jsonPath("$.data.appliedOperations[0].sortKey").value("000000000001I00000000000"));
	}

	@Test
	@DisplayName("성공_block_move는 version 없이도 request shape 검증을 통과한다")
	void applyTransactionsAllowsBlockMoveWithoutVersion() throws Exception {
		UUID documentId = UUID.randomUUID();
		UUID blockId = UUID.randomUUID();

		when(documentTransactionService.apply(eq(documentId), any(), eq(ACTOR_ID)))
			.thenReturn(new DocumentTransactionResult(
				documentId,
				1,
				"batch-move-no-version",
				List.of(
					new DocumentTransactionAppliedOperationResult(
						"op-1",
						DocumentTransactionOperationStatus.APPLIED,
						null,
						blockId,
						1,
						"000000000001I00000000000",
						null
					)
				)
			));

		mockMvc.perform(post("/v1/documents/{documentId}/transactions", documentId)
				.contentType("application/json")
				.header(USER_ID_HEADER, ACTOR_ID)
				.content("""
					{
					  "clientId": "web-editor",
                      "documentVersion": 0,
					  "batchId": "batch-move-no-version",
					  "operations": [
					    {
					      "opId": "op-1",
					      "type": "BLOCK_MOVE",
					      "blockRef": "%s",
					      "parentRef": null,
					      "afterRef": "%s",
					      "beforeRef": null
					    }
					  ]
					}
					""".formatted(blockId, UUID.randomUUID())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.documentVersion").value(1))
			.andExpect(jsonPath("$.data.appliedOperations[0].blockId").value(blockId.toString()))
			.andExpect(jsonPath("$.data.appliedOperations[0].version").value(1));
	}

	@Test
	@DisplayName("성공_block_move no-op transaction 요청에 대해 NO_OP status 응답을 반환한다")
	void applyTransactionsReturnsNoOpStatusForBlockMove() throws Exception {
		UUID documentId = UUID.randomUUID();
		UUID blockId = UUID.randomUUID();

		when(documentTransactionService.apply(eq(documentId), any(), eq(ACTOR_ID)))
			.thenReturn(new DocumentTransactionResult(
				documentId,
				0,
				"batch-no-op",
				List.of(
					new DocumentTransactionAppliedOperationResult(
						"op-1",
						DocumentTransactionOperationStatus.NO_OP,
						null,
						blockId,
						4,
						"000000000001000000000000",
						null
					)
				)
			));

		mockMvc.perform(post("/v1/documents/{documentId}/transactions", documentId)
				.contentType("application/json")
				.header(USER_ID_HEADER, ACTOR_ID)
				.content("""
					{
					  "clientId": "web-editor",
                      "documentVersion": 0,
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
	@DisplayName("성공_replace_content no-op transaction 요청에 대해 NO_OP status 응답을 반환한다")
	void applyTransactionsReturnsNoOpStatusForReplaceContent() throws Exception {
		UUID documentId = UUID.randomUUID();
		UUID blockId = UUID.randomUUID();

		when(documentTransactionService.apply(eq(documentId), any(), eq(ACTOR_ID)))
			.thenReturn(new DocumentTransactionResult(
				documentId,
				0,
				"batch-no-op-replace",
				List.of(
					new DocumentTransactionAppliedOperationResult(
						"op-1",
						DocumentTransactionOperationStatus.NO_OP,
						null,
						blockId,
						3,
						"000000000001000000000000",
						null
					)
				)
			));

		mockMvc.perform(post("/v1/documents/{documentId}/transactions", documentId)
				.contentType("application/json")
				.header(USER_ID_HEADER, ACTOR_ID)
				.content("""
					{
					  "clientId": "web-editor",
                      "documentVersion": 0,
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
	@DisplayName("실패_documentVersion이 없으면 유효성 검사 오류를 반환한다")
	void applyTransactionsRejectsMissingDocumentVersion() throws Exception {
		var result = mockMvc.perform(post("/v1/documents/{documentId}/transactions", UUID.randomUUID())
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "clientId": "web-editor",
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

		ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
	}

	@Test
	@DisplayName("실패_replace_content에 content가 없으면 유효성 검사 오류를 반환한다")
	void applyTransactionsRejectsReplaceContentWithoutContent() throws Exception {
		var result = mockMvc.perform(post("/v1/documents/{documentId}/transactions", UUID.randomUUID())
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "clientId": "web-editor",
                      "documentVersion": 0,
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
	}

	@Test
	@DisplayName("실패_create에 content가 함께 오면 유효성 검사 오류를 반환한다")
	void applyTransactionsRejectsCreateWithContent() throws Exception {
		var result = mockMvc.perform(post("/v1/documents/{documentId}/transactions", UUID.randomUUID())
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "clientId": "web-editor",
                      "documentVersion": 0,
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
	}

	@Test
	@DisplayName("실패_create에 version이 함께 오면 유효성 검사 오류를 반환한다")
	void applyTransactionsRejectsCreateWithVersion() throws Exception {
		var result = mockMvc.perform(post("/v1/documents/{documentId}/transactions", UUID.randomUUID())
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "clientId": "web-editor",
                      "documentVersion": 0,
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
	}

	@Test
	@DisplayName("실패_create에 blockRef가 없으면 유효성 검사 오류를 반환한다")
	void applyTransactionsRejectsCreateWithoutBlockRef() throws Exception {
		var result = mockMvc.perform(post("/v1/documents/{documentId}/transactions", UUID.randomUUID())
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "clientId": "web-editor",
                      "documentVersion": 0,
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
	}

	@Test
	@DisplayName("실패_replace_content에 위치 필드가 함께 오면 유효성 검사 오류를 반환한다")
	void applyTransactionsRejectsReplaceContentWithPositionFields() throws Exception {
		var result = mockMvc.perform(post("/v1/documents/{documentId}/transactions", UUID.randomUUID())
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "clientId": "web-editor",
                      "documentVersion": 0,
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
	}

	@Test
	@DisplayName("성공_block_delete는 version 없이도 request shape 검증을 통과한다")
	void applyTransactionsAllowsBlockDeleteWithoutVersion() throws Exception {
		UUID documentId = UUID.randomUUID();
		UUID blockId = UUID.randomUUID();
		LocalDateTime deletedAt = LocalDateTime.now();

		when(documentTransactionService.apply(eq(documentId), any(), eq(ACTOR_ID)))
			.thenReturn(new DocumentTransactionResult(
				documentId,
				"batch-delete-no-version",
				List.of(
					new DocumentTransactionAppliedOperationResult(
						"op-1",
						DocumentTransactionOperationStatus.APPLIED,
						null,
						blockId,
						null,
						null,
						deletedAt
					)
				)
			));

		mockMvc.perform(post("/v1/documents/{documentId}/transactions", documentId)
				.contentType("application/json")
				.header(USER_ID_HEADER, ACTOR_ID)
				.content("""
					{
					  "clientId": "web-editor",
                      "documentVersion": 0,
					  "batchId": "batch-delete-no-version",
					  "operations": [
					    {
					      "opId": "op-1",
					      "type": "BLOCK_DELETE",
					      "blockRef": "tmp:block:1"
					    }
					  ]
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.appliedOperations[0].blockId").value(blockId.toString()))
			.andExpect(jsonPath("$.data.appliedOperations[0].deletedAt").exists());
	}

	@Test
	@DisplayName("실패_block_move에 content가 함께 오면 유효성 검사 오류를 반환한다")
	void applyTransactionsRejectsBlockMoveWithContent() throws Exception {
		var result = mockMvc.perform(post("/v1/documents/{documentId}/transactions", UUID.randomUUID())
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "clientId": "web-editor",
                      "documentVersion": 0,
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
	}

	@Test
	@DisplayName("실패_transaction 서비스가 잘못된 요청 예외를 던지면 잘못된 요청 응답을 반환한다")
	void applyTransactionsReturnsBadRequestWhenServiceRejectsRequest() throws Exception {
		when(documentTransactionService.apply(any(), any(), eq(ACTOR_ID)))
			.thenThrow(new BusinessException(BusinessErrorCode.INVALID_REQUEST));

		var result = mockMvc.perform(post("/v1/documents/{documentId}/transactions", UUID.randomUUID())
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "clientId": "web-editor",
                      "documentVersion": 0,
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
	@DisplayName("실패_transaction 서비스가 충돌 예외를 던지면 충돌 응답을 반환한다")
	void applyTransactionsReturnsConflictWhenServiceRejectsWithConflict() throws Exception {
		when(documentTransactionService.apply(any(), any(), eq(ACTOR_ID)))
			.thenThrow(new BusinessException(BusinessErrorCode.CONFLICT));

		var result = mockMvc.perform(post("/v1/documents/{documentId}/transactions", UUID.randomUUID())
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "clientId": "web-editor",
                      "documentVersion": 0,
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
	@DisplayName("성공_문서 생성 요청에 대해 생성 응답을 반환한다")
	void createDocumentReturnsCreatedEnvelope() throws Exception {
		UUID workspaceId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();

		when(documentService.create(
			eq(workspaceId),
			eq(parentId),
			eq(PROJECT_OVERVIEW_TITLE),
			eq(ICON_DOC_JSON),
			eq(COVER_1_JSON),
			eq(ACTOR_ID)
		)).thenReturn(document(documentId, workspaceId, parentId, PROJECT_OVERVIEW_TITLE, ACTOR_ID, 0,
			"00000000000000000003",
			ICON_DOC_JSON,
			COVER_1_JSON));

		mockMvc.perform(post("/v1/workspaces/{workspaceId}/documents", workspaceId)
				.contentType("application/json")
				.header(USER_ID_HEADER, ACTOR_ID)
				.content("""
					{
					  "parentId": "%s",
					  "title": "프로젝트 개요",
					  "icon": {
					    "type": "emoji",
					    "value": "📄"
					  },
					  "cover": {
					    "type": "image",
					    "value": "cover-1"
					  }
					}
					""".formatted(parentId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.httpStatus").value("CREATED"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("리소스 생성 성공"))
			.andExpect(jsonPath("$.code").value(201))
			.andExpect(jsonPath("$.data.id").value(documentId.toString()))
			.andExpect(jsonPath("$.data.workspaceId").value(workspaceId.toString()))
			.andExpect(jsonPath("$.data.parentId").value(parentId.toString()))
			.andExpect(jsonPath("$.data.title").value(PROJECT_OVERVIEW_TITLE))
			.andExpect(jsonPath("$.data.sortKey").value("00000000000000000003"))
			.andExpect(jsonPath("$.data.icon.type").value("emoji"))
			.andExpect(jsonPath("$.data.cover.value").value("cover-1"))
			.andExpect(jsonPath("$.data.createdBy").value(ACTOR_ID));
	}

	@Test
	@DisplayName("성공_워크스페이스 문서 목록 조회 요청에 대해 문서 배열 응답을 반환한다")
	void getDocumentsReturnsEnvelope() throws Exception {
		UUID workspaceId = UUID.randomUUID();
		UUID rootDocumentId = UUID.randomUUID();
		UUID childDocumentId = UUID.randomUUID();

		when(documentService.getAllByWorkspaceId(workspaceId)).thenReturn(List.of(
			document(rootDocumentId, workspaceId, null, ROOT_DOCUMENT_TITLE, ACTOR_ID, 0,
				"00000000000000000001", null, null),
			document(childDocumentId, workspaceId, rootDocumentId, CHILD_DOCUMENT_TITLE, ACTOR_ID, 1,
				"00000000000000000002", null, null)
		));

		mockMvc.perform(get("/v1/workspaces/{workspaceId}/documents", workspaceId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data[0].id").value(rootDocumentId.toString()))
			.andExpect(jsonPath("$.data[0].parentId").doesNotExist())
			.andExpect(jsonPath("$.data[0].title").value(ROOT_DOCUMENT_TITLE))
			.andExpect(jsonPath("$.data[0].sortKey").value("00000000000000000001"))
			.andExpect(jsonPath("$.data[1].id").value(childDocumentId.toString()))
			.andExpect(jsonPath("$.data[1].parentId").value(rootDocumentId.toString()))
			.andExpect(jsonPath("$.data[1].sortKey").value("00000000000000000002"))
			.andExpect(jsonPath("$.data[1].version").value(1));
	}

	@Test
	@DisplayName("실패_존재하지 않는 워크스페이스의 문서 목록 조회는 리소스 없음 응답을 반환한다")
	void getDocumentsReturnsNotFoundWhenWorkspaceMissing() throws Exception {
		UUID workspaceId = UUID.randomUUID();
		when(documentService.getAllByWorkspaceId(workspaceId))
			.thenThrow(new BusinessException(BusinessErrorCode.WORKSPACE_NOT_FOUND));

		var result = mockMvc.perform(get("/v1/workspaces/{workspaceId}/documents", workspaceId));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9003, "요청한 워크스페이스를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_문서 제목이 공백이면 유효성 검사 오류를 반환한다")
	void createDocumentRejectsBlankTitle() throws Exception {
		var result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/documents", UUID.randomUUID())
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "title": " "
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
	}

	@Test
	@DisplayName("실패_부모 문서가 다른 워크스페이스에 있으면 잘못된 요청 응답을 반환한다")
	void createDocumentReturnsBadRequestWhenParentIsOutOfWorkspace() throws Exception {
		UUID workspaceId = UUID.randomUUID();

		when(documentService.create(
			eq(workspaceId),
			eq(UUID.fromString("11111111-1111-1111-1111-111111111111")),
			eq(PROJECT_OVERVIEW_TITLE),
			eq(null),
			eq(null),
			eq(ACTOR_ID)
		)).thenThrow(new BusinessException(BusinessErrorCode.INVALID_REQUEST));

		var result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/documents", workspaceId)
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "parentId": "11111111-1111-1111-1111-111111111111",
				  "title": "프로젝트 개요"
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9015, "잘못된 요청입니다.");
	}

	@Test
	@DisplayName("실패_부모 문서가 없으면 문서 없음 응답을 반환한다")
	void createDocumentReturnsNotFoundWhenParentDocumentMissing() throws Exception {
		UUID workspaceId = UUID.randomUUID();
		UUID parentId = UUID.fromString("11111111-1111-1111-1111-111111111111");

		when(documentService.create(
			eq(workspaceId),
			eq(parentId),
			eq(PROJECT_OVERVIEW_TITLE),
			eq(null),
			eq(null),
			eq(ACTOR_ID)
		)).thenThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));

		var result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/documents", workspaceId)
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "parentId": "11111111-1111-1111-1111-111111111111",
				  "title": "프로젝트 개요"
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("성공_문서 단건 조회 요청에 대해 문서 응답을 반환한다")
	void getDocumentReturnsEnvelope() throws Exception {
		UUID workspaceId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();

		when(documentService.getById(documentId))
			.thenReturn(document(documentId, workspaceId, null, PROJECT_OVERVIEW_TITLE, ACTOR_ID, 2,
				"00000000000000000007",
				ICON_DOC_JSON,
				null));

		mockMvc.perform(get("/v1/documents/{documentId}", documentId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data.id").value(documentId.toString()))
			.andExpect(jsonPath("$.data.workspaceId").value(workspaceId.toString()))
			.andExpect(jsonPath("$.data.title").value(PROJECT_OVERVIEW_TITLE))
			.andExpect(jsonPath("$.data.sortKey").value("00000000000000000007"))
			.andExpect(jsonPath("$.data.icon.value").value("📄"))
			.andExpect(jsonPath("$.data.version").value(2));
	}

	@Test
	@DisplayName("실패_존재하지 않는 문서 단건 조회는 리소스 없음 응답을 반환한다")
	void getDocumentReturnsNotFoundWhenDocumentMissing() throws Exception {
		UUID documentId = UUID.randomUUID();
		when(documentService.getById(documentId))
			.thenThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));

		var result = mockMvc.perform(get("/v1/documents/{documentId}", documentId));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("성공_soft delete된 문서 복구 요청에 대해 성공 응답을 반환한다")
	void restoreDocumentReturnsSuccessEnvelope() throws Exception {
		UUID documentId = UUID.randomUUID();

		mockMvc.perform(post("/v1/documents/{documentId}/restore", documentId)
				.header(USER_ID_HEADER, ACTOR_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("요청 응답 성공"))
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data").doesNotExist());

		verify(documentService).restore(documentId, ACTOR_ID);
	}

	@Test
	@DisplayName("실패_존재하지 않는 문서 복구 요청은 문서 없음 응답을 반환한다")
	void restoreDocumentReturnsNotFoundWhenDocumentMissing() throws Exception {
		UUID documentId = UUID.randomUUID();
		doThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND))
			.when(documentService).restore(documentId, ACTOR_ID);

		var result = mockMvc.perform(post("/v1/documents/{documentId}/restore", documentId)
			.header(USER_ID_HEADER, ACTOR_ID));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_이미 활성 상태인 문서 복구 요청은 문서 없음 응답을 반환한다")
	void restoreDocumentReturnsNotFoundWhenDocumentAlreadyActive() throws Exception {
		UUID documentId = UUID.randomUUID();
		doThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND))
			.when(documentService).restore(documentId, ACTOR_ID);

		var result = mockMvc.perform(post("/v1/documents/{documentId}/restore", documentId)
			.header(USER_ID_HEADER, ACTOR_ID));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_사용자 식별자 헤더 없이 문서 복구 요청하면 인증 오류 응답을 반환한다")
	void restoreDocumentReturnsUnauthorizedWhenHeaderMissing() throws Exception {
		UUID documentId = UUID.randomUUID();

		var result = mockMvc.perform(post("/v1/documents/{documentId}/restore", documentId));

		ApiResponseAssertions.assertErrorEnvelope(result, "UNAUTHORIZED", 9001, "인증 정보가 없습니다.");
		verify(documentService, never()).restore(any(), any());
	}

	@Test
	@DisplayName("성공_문서 move 요청에 대해 성공 응답을 반환한다")
	void moveDocumentReturnsSuccessEnvelope() throws Exception {
		UUID documentId = UUID.randomUUID();

		mockMvc.perform(post("/v1/documents/{documentId}/move", documentId)
				.contentType("application/json")
				.header(USER_ID_HEADER, ACTOR_ID)
				.content("""
					{
					  "targetParentId": null,
					  "afterDocumentId": null,
					  "beforeDocumentId": null
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("요청 응답 성공"))
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data").doesNotExist());

		verify(documentService).move(documentId, null, null, null, ACTOR_ID);
	}

	@Test
	@DisplayName("실패_존재하지 않는 문서 move 요청은 문서 없음 응답을 반환한다")
	void moveDocumentReturnsNotFoundWhenDocumentMissing() throws Exception {
		UUID documentId = UUID.randomUUID();
		doThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND))
			.when(documentService).move(documentId, null, null, null, ACTOR_ID);

		var result = mockMvc.perform(post("/v1/documents/{documentId}/move", documentId)
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "targetParentId": null,
				  "afterDocumentId": null,
				  "beforeDocumentId": null
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_삭제된 문서 move 요청은 문서 없음 응답을 반환한다")
	void moveDocumentReturnsNotFoundWhenDocumentDeleted() throws Exception {
		UUID documentId = UUID.randomUUID();
		doThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND))
			.when(documentService).move(documentId, null, null, null, ACTOR_ID);

		var result = mockMvc.perform(post("/v1/documents/{documentId}/move", documentId)
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "targetParentId": null,
				  "afterDocumentId": null,
				  "beforeDocumentId": null
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_자기 자신을 부모로 지정한 문서 move 요청은 잘못된 요청 응답을 반환한다")
	void moveDocumentReturnsBadRequestWhenParentIsSelf() throws Exception {
		UUID documentId = UUID.randomUUID();
		doThrow(new BusinessException(BusinessErrorCode.INVALID_REQUEST))
			.when(documentService).move(documentId, documentId, null, null, ACTOR_ID);

		var result = mockMvc.perform(post("/v1/documents/{documentId}/move", documentId)
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "targetParentId": "%s",
				  "afterDocumentId": null,
				  "beforeDocumentId": null
				}
				""".formatted(documentId)));

		ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9015, "잘못된 요청입니다.");
	}

	@Test
	@DisplayName("실패_사용자 식별자 헤더 없이 문서 move 요청하면 인증 오류 응답을 반환한다")
	void moveDocumentReturnsUnauthorizedWhenHeaderMissing() throws Exception {
		UUID documentId = UUID.randomUUID();

		var result = mockMvc.perform(post("/v1/documents/{documentId}/move", documentId)
			.contentType("application/json")
			.content("""
				{
				  "targetParentId": null,
				  "afterDocumentId": null,
				  "beforeDocumentId": null
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "UNAUTHORIZED", 9001, "인증 정보가 없습니다.");
		verify(documentService, never()).move(any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("실패_icon이 객체 스키마를 따르지 않으면 유효성 검사 오류를 반환한다")
	void createDocumentRejectsInvalidIconSchema() throws Exception {
		var result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/documents", UUID.randomUUID())
			.contentType("application/json")
			.header("X-User-Id", "user-123")
			.content("""
				{
				  "title": "프로젝트 개요",
				  "icon": "📄"
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
	}

	@Test
	@DisplayName("실패_cover가 필수 필드를 누락하면 유효성 검사 오류를 반환한다")
	void createDocumentRejectsInvalidCoverSchema() throws Exception {
		var result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/documents", UUID.randomUUID())
			.contentType("application/json")
			.header("X-User-Id", "user-123")
			.content("""
				{
				  "title": "프로젝트 개요",
				  "cover": {
				    "type": "image"
				  }
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
	}

	@Test
	@DisplayName("실패_인증 헤더가 없으면 인증 오류를 반환한다")
	void createDocumentRequiresUserHeader() throws Exception {
		var result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/documents", UUID.randomUUID())
			.contentType("application/json")
			.content("""
				{
				  "title": "프로젝트 개요"
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "UNAUTHORIZED", 9001, "인증 정보가 없습니다.");
	}

	@Test
	@DisplayName("성공_문서 수정 요청에 대해 수정 응답을 반환한다")
	void updateDocumentReturnsEnvelope() throws Exception {
		UUID workspaceId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();

		when(documentService.update(
			eq(documentId),
			eq(UPDATED_PROJECT_OVERVIEW_TITLE),
			eq(ICON_DOC_JSON),
			eq(COVER_2_JSON),
			eq(parentId),
			eq(ACTOR_ID)
		)).thenReturn(document(documentId, workspaceId, parentId, UPDATED_PROJECT_OVERVIEW_TITLE, ACTOR_ID, 3,
			"00000000000000000007",
			ICON_DOC_JSON,
			COVER_2_JSON));

		mockMvc.perform(patch("/v1/documents/{documentId}", documentId)
				.contentType("application/json")
				.header(USER_ID_HEADER, ACTOR_ID)
				.content("""
					{
					  "title": "수정된 프로젝트 개요",
					  "parentId": "%s",
					  "icon": {
					    "type": "emoji",
					    "value": "📄"
					  },
					  "cover": {
					    "type": "image",
					    "value": "cover-2"
					  }
					}
					""".formatted(parentId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.message").value("요청 응답 성공"))
			.andExpect(jsonPath("$.data.id").value(documentId.toString()))
			.andExpect(jsonPath("$.data.parentId").value(parentId.toString()))
			.andExpect(jsonPath("$.data.title").value(UPDATED_PROJECT_OVERVIEW_TITLE))
			.andExpect(jsonPath("$.data.cover.value").value("cover-2"))
			.andExpect(jsonPath("$.data.version").value(3));
	}

	@Test
	@DisplayName("실패_존재하지 않는 문서를 수정하면 문서 없음 응답을 반환한다")
	void updateDocumentReturnsNotFoundWhenDocumentMissing() throws Exception {
		UUID documentId = UUID.randomUUID();

		when(documentService.update(
			eq(documentId),
			eq(UPDATED_PROJECT_OVERVIEW_TITLE),
			eq(null),
			eq(null),
			eq(null),
			eq(ACTOR_ID)
		)).thenThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));

		var result = mockMvc.perform(patch("/v1/documents/{documentId}", documentId)
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "title": "수정된 프로젝트 개요"
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_parentId가 자기 자신이면 잘못된 요청 응답을 반환한다")
	void updateDocumentReturnsBadRequestWhenParentIsSelf() throws Exception {
		UUID documentId = UUID.randomUUID();

		when(documentService.update(
			eq(documentId),
			eq(UPDATED_PROJECT_OVERVIEW_TITLE),
			eq(null),
			eq(null),
			eq(documentId),
			eq(ACTOR_ID)
		)).thenThrow(new BusinessException(BusinessErrorCode.INVALID_REQUEST));

		var result = mockMvc.perform(patch("/v1/documents/{documentId}", documentId)
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "title": "수정된 프로젝트 개요",
				  "parentId": "%s"
				}
				""".formatted(documentId)));

		ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9015, "잘못된 요청입니다.");
	}

	@Test
	@DisplayName("실패_다른 워크스페이스 부모 문서를 지정하면 잘못된 요청 응답을 반환한다")
	void updateDocumentReturnsBadRequestWhenParentIsOutOfWorkspace() throws Exception {
		UUID documentId = UUID.randomUUID();
		UUID parentId = UUID.fromString("11111111-1111-1111-1111-111111111111");

		when(documentService.update(
			eq(documentId),
			eq(UPDATED_PROJECT_OVERVIEW_TITLE),
			eq(null),
			eq(null),
			eq(parentId),
			eq(ACTOR_ID)
		)).thenThrow(new BusinessException(BusinessErrorCode.INVALID_REQUEST));

		var result = mockMvc.perform(patch("/v1/documents/{documentId}", documentId)
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "title": "수정된 프로젝트 개요",
				  "parentId": "11111111-1111-1111-1111-111111111111"
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9015, "잘못된 요청입니다.");
	}

	@Test
	@DisplayName("실패_문서 수정 요청에 제목이 없으면 유효성 검사 오류를 반환한다")
	void updateDocumentRejectsMissingTitle() throws Exception {
		UUID documentId = UUID.randomUUID();

		var result = mockMvc.perform(patch("/v1/documents/{documentId}", documentId)
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "parentId": null
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
		verifyNoInteractions(documentService);
	}

	@Test
	@DisplayName("실패_문서 수정 제목이 공백이면 유효성 검사 오류를 반환한다")
	void updateDocumentRejectsBlankTitle() throws Exception {
		UUID documentId = UUID.randomUUID();

		var result = mockMvc.perform(patch("/v1/documents/{documentId}", documentId)
			.contentType("application/json")
			.header("X-User-Id", "user-123")
			.content("""
				{
				  "title": " "
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
		verifyNoInteractions(documentService);
	}

	@Test
	@DisplayName("실패_문서 수정 요청에 인증 헤더가 없으면 인증 오류를 반환한다")
	void updateDocumentRequiresUserHeader() throws Exception {
		UUID documentId = UUID.randomUUID();

		var result = mockMvc.perform(patch("/v1/documents/{documentId}", documentId)
			.contentType("application/json")
			.content("""
				{
				  "title": "수정된 프로젝트 개요"
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "UNAUTHORIZED", 9001, "인증 정보가 없습니다.");
	}

	@Test
	@DisplayName("성공_정상 삭제 요청 시 성공 응답을 반환한다")
	void deleteDocumentReturnsSuccessEnvelope() throws Exception {
		UUID documentId = UUID.randomUUID();
		doNothing().when(documentService).delete(documentId, ACTOR_ID);

		mockMvc.perform(delete("/v1/documents/{documentId}", documentId)
				.header(USER_ID_HEADER, ACTOR_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("요청 응답 성공"))
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data").doesNotExist());

		verify(documentService).delete(documentId, ACTOR_ID);
	}

	@Test
	@DisplayName("실패_존재하지 않는 문서 삭제 시 문서 없음 응답을 반환한다")
	void deleteDocumentReturnsNotFoundWhenDocumentMissing() throws Exception {
		UUID documentId = UUID.randomUUID();
		doThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND))
			.when(documentService).delete(documentId, ACTOR_ID);

		var result = mockMvc.perform(delete("/v1/documents/{documentId}", documentId)
			.header(USER_ID_HEADER, ACTOR_ID));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_이미 soft delete된 문서 삭제 시 문서 없음 응답을 반환한다")
	void deleteDocumentReturnsNotFoundWhenDocumentAlreadyDeleted() throws Exception {
		UUID documentId = UUID.randomUUID();
		doThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND))
			.when(documentService).delete(documentId, ACTOR_ID);

		var result = mockMvc.perform(delete("/v1/documents/{documentId}", documentId)
			.header(USER_ID_HEADER, ACTOR_ID));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_X-User-Id 헤더가 없으면 인증 오류를 반환한다")
	void deleteDocumentReturnsUnauthorizedWhenHeaderMissing() throws Exception {
		UUID documentId = UUID.randomUUID();

		var result = mockMvc.perform(delete("/v1/documents/{documentId}", documentId));

		ApiResponseAssertions.assertErrorEnvelope(result, "UNAUTHORIZED", 9001, "인증 정보가 없습니다.");
	}
}
