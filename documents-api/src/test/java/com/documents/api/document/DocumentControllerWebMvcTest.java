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

import com.documents.api.document.support.DocumentJsonCodec;
import com.documents.api.exception.GlobalExceptionHandler;
import com.documents.api.support.ApiResponseAssertions;
import com.documents.domain.Document;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("Document 컨트롤러 빠른 검증")
class DocumentControllerWebMvcTest {

	@Mock
	private DocumentService documentService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();

		mockMvc = MockMvcBuilders.standaloneSetup(new DocumentController(
				documentService,
				new DocumentApiMapper(new DocumentJsonCodec(new ObjectMapper()))
			))
			.setControllerAdvice(new GlobalExceptionHandler())
			.setValidator(validator)
			.build();
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
			eq("프로젝트 개요"),
			eq("{\"type\":\"emoji\",\"value\":\"📄\"}"),
			eq("{\"type\":\"image\",\"value\":\"cover-1\"}"),
			eq("user-123")
		)).thenReturn(document(documentId, workspaceId, parentId, "프로젝트 개요", "user-123", 0,
			"00000000000000000003",
			"{\"type\":\"emoji\",\"value\":\"📄\"}",
			"{\"type\":\"image\",\"value\":\"cover-1\"}"));

		mockMvc.perform(post("/v1/workspaces/{workspaceId}/documents", workspaceId)
				.contentType("application/json")
				.header("X-User-Id", "user-123")
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
			.andExpect(jsonPath("$.data.title").value("프로젝트 개요"))
			.andExpect(jsonPath("$.data.sortKey").value("00000000000000000003"))
			.andExpect(jsonPath("$.data.icon.type").value("emoji"))
			.andExpect(jsonPath("$.data.cover.value").value("cover-1"))
			.andExpect(jsonPath("$.data.createdBy").value("user-123"));
	}

	@Test
	@DisplayName("성공_워크스페이스 문서 목록 조회 요청에 대해 문서 배열 응답을 반환한다")
	void getDocumentsReturnsEnvelope() throws Exception {
		UUID workspaceId = UUID.randomUUID();
		UUID rootDocumentId = UUID.randomUUID();
		UUID childDocumentId = UUID.randomUUID();

		when(documentService.getAllByWorkspaceId(workspaceId)).thenReturn(List.of(
			document(rootDocumentId, workspaceId, null, "루트 문서", "user-123", 0,
				"00000000000000000001", null, null),
			document(childDocumentId, workspaceId, rootDocumentId, "하위 문서", "user-123", 1,
				"00000000000000000002", null, null)
		));

		mockMvc.perform(get("/v1/workspaces/{workspaceId}/documents", workspaceId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data[0].id").value(rootDocumentId.toString()))
			.andExpect(jsonPath("$.data[0].parentId").doesNotExist())
			.andExpect(jsonPath("$.data[0].title").value("루트 문서"))
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
			.header("X-User-Id", "user-123")
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
			eq("프로젝트 개요"),
			eq(null),
			eq(null),
			eq("user-123")
		)).thenThrow(new BusinessException(BusinessErrorCode.INVALID_REQUEST));

		var result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/documents", workspaceId)
			.contentType("application/json")
			.header("X-User-Id", "user-123")
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
			eq("프로젝트 개요"),
			eq(null),
			eq(null),
			eq("user-123")
		)).thenThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));

		var result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/documents", workspaceId)
			.contentType("application/json")
			.header("X-User-Id", "user-123")
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
			.thenReturn(document(documentId, workspaceId, null, "프로젝트 개요", "user-123", 2,
				"00000000000000000007",
				"{\"type\":\"emoji\",\"value\":\"📄\"}",
				null));

		mockMvc.perform(get("/v1/documents/{documentId}", documentId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data.id").value(documentId.toString()))
			.andExpect(jsonPath("$.data.workspaceId").value(workspaceId.toString()))
			.andExpect(jsonPath("$.data.title").value("프로젝트 개요"))
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
			.workspaceId(workspaceId)
			.parentId(parentId)
			.title(title)
			.sortKey(sortKey)
			.iconJson(iconJson)
			.coverJson(coverJson)
			.createdBy(actorId)
			.updatedBy(actorId)
			.build();
		document.setCreatedAt(LocalDateTime.of(2026, 3, 16, 0, 0));
		document.setUpdatedAt(LocalDateTime.of(2026, 3, 16, 0, 0));
		document.setVersion(version);
		return document;
	}

	@Test
	@DisplayName("성공_문서 수정 요청에 대해 수정 응답을 반환한다")
	void updateDocumentReturnsEnvelope() throws Exception {
		UUID workspaceId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();

		when(documentService.update(
			eq(documentId),
			eq("수정된 프로젝트 개요"),
			eq("{\"type\":\"emoji\",\"value\":\"📄\"}"),
			eq("{\"type\":\"image\",\"value\":\"cover-2\"}"),
			eq(parentId),
			eq("user-123")
		)).thenReturn(document(documentId, workspaceId, parentId, "수정된 프로젝트 개요", "user-123", 3,
			"00000000000000000007",
			"{\"type\":\"emoji\",\"value\":\"📄\"}",
			"{\"type\":\"image\",\"value\":\"cover-2\"}"));

		mockMvc.perform(patch("/v1/documents/{documentId}", documentId)
				.contentType("application/json")
				.header("X-User-Id", "user-123")
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
			.andExpect(jsonPath("$.data.title").value("수정된 프로젝트 개요"))
			.andExpect(jsonPath("$.data.cover.value").value("cover-2"))
			.andExpect(jsonPath("$.data.version").value(3));
	}

}
