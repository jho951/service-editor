package com.documents.api.document;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.documents.domain.Block;
import com.documents.domain.BlockType;
import com.documents.domain.Document;
import com.documents.domain.DocumentVisibility;
import com.documents.domain.Workspace;
import com.documents.repository.BlockRepository;
import com.documents.repository.DocumentRepository;
import com.documents.repository.WorkspaceRepository;
import com.documents.service.DocumentService;

@SpringBootTest
@AutoConfigureMockMvc
@Import(DocumentApiIntegrationTest.DocumentDeleteSqlCounterTestConfig.class)
@DisplayName("Document API 통합 검증")
class DocumentApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WorkspaceRepository workspaceRepository;

	@Autowired
	private DocumentRepository documentRepository;

	@Autowired
	private BlockRepository blockRepository;

	@Autowired
	private DocumentDeleteSqlCounter documentDeleteSqlCounter;

	@Autowired
	private DocumentService documentService;

	@BeforeEach
	void setUp() {
		blockRepository.deleteAll();
		documentRepository.deleteAll();
		workspaceRepository.deleteAll();
		documentDeleteSqlCounter.reset();
	}

	@Test
	@DisplayName("성공_문서 생성 API는 워크스페이스 하위에 루트 문서를 저장하고 응답한다")
	void createDocumentReturnsCreatedEnvelope() throws Exception {
		Workspace workspace = workspace("Docs Root");

		mockMvc.perform(post("/v1/workspaces/{workspaceId}/documents", workspace.getId())
				.contentType("application/json")
				.header("X-User-Id", "user-123")
				.content("""
					{
					  "title": "프로젝트 개요",
					  "icon": {
					    "type": "emoji",
					    "value": "📄"
					  }
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.httpStatus").value("CREATED"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("리소스 생성 성공"))
			.andExpect(jsonPath("$.code").value(201))
			.andExpect(jsonPath("$.data.workspaceId").value(workspace.getId().toString()))
			.andExpect(jsonPath("$.data.parentId").doesNotExist())
			.andExpect(jsonPath("$.data.title").value("프로젝트 개요"))
			.andExpect(jsonPath("$.data.sortKey").value("000000000001000000000000"))
			.andExpect(jsonPath("$.data.icon.type").value("emoji"))
			.andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
			.andExpect(jsonPath("$.data.createdBy").value("user-123"))
			.andExpect(jsonPath("$.data.version").value(0));

		assertThat(documentRepository.findAll()).hasSize(1);
		Document savedDocument = documentRepository.findAll().get(0);
		assertThat(savedDocument.getWorkspaceId()).isEqualTo(workspace.getId());
		assertThat(savedDocument.getParentId()).isNull();
		assertThat(savedDocument.getTitle()).isEqualTo("프로젝트 개요");
		assertThat(savedDocument.getSortKey()).isEqualTo("000000000001000000000000");
		assertThat(savedDocument.getIconJson()).isEqualTo("{\"type\":\"emoji\",\"value\":\"📄\"}");
		assertThat(savedDocument.getCreatedBy()).isEqualTo("user-123");
	}

	@Test
	@DisplayName("성공_워크스페이스 문서 목록 조회 API는 soft delete되지 않은 문서 목록을 반환한다")
	void getDocumentsReturnsDocumentList() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document rootDocument = saveDocument(workspace.getId(), null, "루트 문서", "00000000000000000001");
		saveDeletedDocument(workspace.getId(), "삭제된 문서", "00000000000000000099");
		saveDocument(workspace.getId(), rootDocument.getId(), "하위 문서", "00000000000000000002");

		mockMvc.perform(get("/v1/workspaces/{workspaceId}/documents", workspace.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].title").value("루트 문서"))
			.andExpect(jsonPath("$.data[1].title").value("하위 문서"));
	}

	@Test
	@DisplayName("성공_워크스페이스 휴지통 문서 목록 조회 API는 휴지통 문서와 자동 영구 삭제 예정 시각을 반환한다")
	void getTrashDocumentsReturnsTrashDocumentList() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document newerDeletedDocument = saveDeletedDocument(
			workspace.getId(),
			"최근 삭제 문서",
			"00000000000000000001",
			LocalDateTime.of(2026, 3, 25, 12, 0, 0)
		);
		Document olderDeletedDocument = documentRepository.save(Document.builder()
			.id(UUID.randomUUID())
			.workspace(workspaceRepository.getReferenceById(workspace.getId()))
			.parent(documentRepository.getReferenceById(newerDeletedDocument.getId()))
			.title("이전 삭제 문서")
			.sortKey("00000000000000000002")
			.deletedAt(LocalDateTime.of(2026, 3, 25, 11, 55, 0))
			.build());
		saveDocument(workspace.getId(), null, "활성 문서", "00000000000000000003");

		mockMvc.perform(get("/v1/workspaces/{workspaceId}/trash/documents", workspace.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].documentId").value(newerDeletedDocument.getId().toString()))
			.andExpect(jsonPath("$.data[0].title").value("최근 삭제 문서"))
			.andExpect(jsonPath("$.data[0].parentId").doesNotExist())
			.andExpect(jsonPath("$.data[0].deletedAt").value("2026-03-25T12:00:00"))
			.andExpect(jsonPath("$.data[0].purgeAt").value("2026-03-25T12:05:00"))
			.andExpect(jsonPath("$.data[1].documentId").value(olderDeletedDocument.getId().toString()))
			.andExpect(jsonPath("$.data[1].parentId").value(newerDeletedDocument.getId().toString()))
			.andExpect(jsonPath("$.data[1].deletedAt").value("2026-03-25T11:55:00"))
			.andExpect(jsonPath("$.data[1].purgeAt").value("2026-03-25T12:00:00"));
	}

	@Test
	@DisplayName("실패_존재하지 않는 워크스페이스의 문서 목록 조회는 리소스 없음 응답을 반환한다")
	void getDocumentsReturnsNotFoundWhenWorkspaceMissing() throws Exception {
		var result = mockMvc.perform(get("/v1/workspaces/{workspaceId}/documents", UUID.randomUUID()));

		assertErrorEnvelope(result, "NOT_FOUND", 9003, "요청한 워크스페이스를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_존재하지 않는 워크스페이스의 휴지통 문서 목록 조회는 리소스 없음 응답을 반환한다")
	void getTrashDocumentsReturnsNotFoundWhenWorkspaceMissing() throws Exception {
		var result = mockMvc.perform(get("/v1/workspaces/{workspaceId}/trash/documents", UUID.randomUUID()));

		assertErrorEnvelope(result, "NOT_FOUND", 9003, "요청한 워크스페이스를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_존재하지 않는 워크스페이스로 문서를 생성하면 리소스 없음 응답을 반환한다")
	void createDocumentReturnsNotFoundWhenWorkspaceMissing() throws Exception {
		var result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/documents", UUID.randomUUID())
			.contentType("application/json")
			.header("X-User-Id", "user-123")
			.content("""
				{
				  "title": "프로젝트 개요"
				}
				"""));

		assertErrorEnvelope(result, "NOT_FOUND", 9003, "요청한 워크스페이스를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_같은 워크스페이스 휴지통 문서와 제목이 중복되면 문서 생성은 충돌 응답을 반환한다")
	void createDocumentReturnsConflictWhenTitleAlreadyExistsInWorkspaceTrash() throws Exception {
		Workspace workspace = workspace("Docs Root");
		saveDeletedDocument(workspace.getId(), "프로젝트 개요", "00000000000000000001");

		var result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/documents", workspace.getId())
			.contentType("application/json")
			.header("X-User-Id", "user-123")
			.content("""
				{
				  "title": "프로젝트 개요"
				}
				"""));

		assertErrorEnvelope(result, "CONFLICT", 9005, "요청이 현재 리소스 상태와 충돌합니다.");
	}

	@Test
	@DisplayName("실패_부모 문서가 다른 워크스페이스에 있으면 잘못된 요청 응답을 반환한다")
	void createDocumentReturnsBadRequestWhenParentBelongsToOtherWorkspace() throws Exception {
		Workspace rootWorkspace = workspaceRepository.save(Workspace.builder()
			.id(UUID.randomUUID())
			.name("Root Workspace")
			.build());
		Workspace otherWorkspace = workspaceRepository.save(Workspace.builder()
			.id(UUID.randomUUID())
			.name("Other Workspace")
			.build());

		Document parentDocument = documentRepository.save(Document.builder()
			.id(UUID.randomUUID())
			.workspace(otherWorkspace)
			.title("다른 워크스페이스 문서")
			.sortKey("00000000000000000001")
			.build());

		var result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/documents", rootWorkspace.getId())
			.contentType("application/json")
			.header("X-User-Id", "user-123")
			.content("""
				{
				  "parentId": "%s",
				  "title": "프로젝트 개요"
				}
				""".formatted(parentDocument.getId())));

		assertErrorEnvelope(result, "BAD_REQUEST", 9015, "잘못된 요청입니다.");
	}

	@Test
	@DisplayName("실패_icon JSON 스키마가 잘못되면 유효성 검사 오류를 반환한다")
	void createDocumentReturnsValidationErrorWhenIconSchemaInvalid() throws Exception {
		Workspace workspace = workspace("Docs Root");

		var result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/documents", workspace.getId())
			.contentType("application/json")
			.header("X-User-Id", "user-123")
			.content("""
				{
				  "title": "프로젝트 개요",
				  "icon": "📄"
				}
				"""));

		assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
	}

	@Test
	@DisplayName("실패_cover JSON 스키마가 잘못되면 유효성 검사 오류를 반환한다")
	void createDocumentReturnsValidationErrorWhenCoverSchemaInvalid() throws Exception {
		Workspace workspace = workspace("Docs Root");

		var result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/documents", workspace.getId())
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

		assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
	}

	@Test
	@DisplayName("실패_인증 헤더가 없으면 문서 생성 API는 인증 오류를 반환한다")
	void createDocumentReturnsUnauthorizedWhenHeaderMissing() throws Exception {
		Workspace workspace = workspace("Docs Root");

		var result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/documents", workspace.getId())
			.contentType("application/json")
			.content("""
				{
				  "title": "프로젝트 개요"
				}
				"""));

		assertErrorEnvelope(result, "UNAUTHORIZED", 9001, "인증 정보가 없습니다.");
	}

	@Test
	@DisplayName("성공_문서 단건 조회 API는 저장된 문서 메타데이터를 반환한다")
	void getDocumentReturnsDocumentEnvelope() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document document = saveDocument(workspace.getId(), null, "프로젝트 개요", "00000000000000000001",
			"{\"type\":\"emoji\",\"value\":\"📄\"}", null, "user-123", "user-123");

		mockMvc.perform(get("/v1/documents/{documentId}", document.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data.id").value(document.getId().toString()))
			.andExpect(jsonPath("$.data.workspaceId").value(workspace.getId().toString()))
			.andExpect(jsonPath("$.data.title").value("프로젝트 개요"))
			.andExpect(jsonPath("$.data.icon.type").value("emoji"))
			.andExpect(jsonPath("$.data.createdBy").value("user-123"));
	}

	@Test
	@DisplayName("실패_soft delete된 문서 단건 조회는 리소스 없음 응답을 반환한다")
	void getDocumentReturnsNotFoundWhenDeleted() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document document = saveDeletedDocument(workspace.getId(), "삭제된 문서", "00000000000000000001");

		var result = mockMvc.perform(get("/v1/documents/{documentId}", document.getId()));

		assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("성공_문서 수정 API는 제목 trim과 메타데이터 변경과 updatedBy 갱신을 반영한다")
	void updateDocumentPersistsServiceLogic() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document document = saveDocument(workspace.getId(), null, "기존 제목", "00000000000000000002",
			"{\"type\":\"emoji\",\"value\":\"😀\"}", "{\"type\":\"image\",\"value\":\"cover-1\"}", null, "user-123");

		mockMvc.perform(patch("/v1/documents/{documentId}", document.getId())
				.contentType("application/json")
				.header("X-User-Id", "user-456")
				.content("""
					{
					  "title": "  수정된 제목  ",
					  "icon": null,
					  "cover": null,
					  "version": 0
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.title").value("수정된 제목"))
			.andExpect(jsonPath("$.data.parentId").doesNotExist())
			.andExpect(jsonPath("$.data.icon").doesNotExist())
			.andExpect(jsonPath("$.data.cover").doesNotExist())
			.andExpect(jsonPath("$.data.updatedBy").value("user-456"))
			.andExpect(jsonPath("$.data.version").value(1));

		Document updatedDocument = documentRepository.findById(document.getId()).orElseThrow();
		assertThat(updatedDocument.getTitle()).isEqualTo("수정된 제목");
		assertThat(updatedDocument.getParentId()).isNull();
		assertThat(updatedDocument.getIconJson()).isNull();
		assertThat(updatedDocument.getCoverJson()).isNull();
		assertThat(updatedDocument.getUpdatedBy()).isEqualTo("user-456");
	}

	@Test
	@DisplayName("실패_빈 제목으로 문서를 수정하면 유효성 검사 오류를 반환한다")
	void updateDocumentReturnsValidationErrorWhenTitleBlank() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document document = saveDocument(workspace.getId(), null, "기존 제목", "00000000000000000001");

		var result = mockMvc.perform(patch("/v1/documents/{documentId}", document.getId())
			.contentType("application/json")
			.header("X-User-Id", "user-123")
				.content("""
					{
					  "title": "   ",
					  "version": 0
					}
					"""));

		assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
	}

	@Test
	@DisplayName("실패_같은 워크스페이스 다른 문서와 제목이 중복되면 문서 수정은 충돌 응답을 반환한다")
	void updateDocumentReturnsConflictWhenTitleAlreadyExists() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document targetDocument = saveDocument(workspace.getId(), null, "기존 제목", "00000000000000000001");
		saveDocument(workspace.getId(), null, "중복 제목", "00000000000000000002");

		var result = mockMvc.perform(patch("/v1/documents/{documentId}", targetDocument.getId())
			.contentType("application/json")
			.header("X-User-Id", "user-123")
				.content("""
					{
					  "title": "중복 제목",
					  "version": 0
					}
					"""));

		assertErrorEnvelope(result, "CONFLICT", 9005, "요청이 현재 리소스 상태와 충돌합니다.");
	}

	@Test
	@DisplayName("실패_존재하지 않는 문서를 수정하면 리소스 없음 응답을 반환한다")
	void updateDocumentReturnsNotFoundWhenDocumentMissing() throws Exception {
		var result = mockMvc.perform(patch("/v1/documents/{documentId}", UUID.randomUUID())
			.contentType("application/json")
			.header("X-User-Id", "user-123")
				.content("""
					{
					  "title": "수정된 제목",
					  "version": 0
					}
					"""));

		assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_문서 수정 요청에 제목이 없으면 유효성 검사 오류를 반환한다")
	void updateDocumentReturnsValidationErrorWhenTitleMissing() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document document = saveDocument(workspace.getId(), null, "기존 제목", "00000000000000000001");

		var result = mockMvc.perform(patch("/v1/documents/{documentId}", document.getId())
			.contentType("application/json")
			.header("X-User-Id", "user-123")
				.content("""
					{
					  "icon": null,
					  "version": 0
					}
					"""));

		assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
	}

	@Test
	@DisplayName("성공_문서 공개 상태 변경 API는 PUBLIC과 PRIVATE 전환을 반영한다")
	void updateDocumentVisibilityPersistsVisibilityAndVersion() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document document = saveDocument(workspace.getId(), null, "공개 상태 대상", "00000000000000000001");

		mockMvc.perform(patch("/v1/documents/{documentId}/visibility", document.getId())
				.contentType("application/json")
				.header("X-User-Id", "user-123")
				.content("""
					{
					  "visibility": "PUBLIC",
					  "version": 0
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
			.andExpect(jsonPath("$.data.version").value(1));

		Document updatedDocument = documentRepository.findById(document.getId()).orElseThrow();
		assertThat(updatedDocument.getVisibility()).isEqualTo(DocumentVisibility.PUBLIC);
		assertThat(updatedDocument.getUpdatedBy()).isEqualTo("user-123");
	}

	@Test
	@DisplayName("성공_같은 공개 상태 요청이면 no-op으로 처리하고 version을 유지한다")
	void updateDocumentVisibilityKeepsVersionWhenRequestedStateIsSame() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document document = saveDocument(workspace.getId(), null, "공개 상태 대상", "00000000000000000001");

		mockMvc.perform(patch("/v1/documents/{documentId}/visibility", document.getId())
				.contentType("application/json")
				.header("X-User-Id", "user-123")
				.content("""
					{
					  "visibility": "PRIVATE",
					  "version": 0
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
			.andExpect(jsonPath("$.data.version").value(0));

		Document updatedDocument = documentRepository.findById(document.getId()).orElseThrow();
		assertThat(updatedDocument.getVisibility()).isEqualTo(DocumentVisibility.PRIVATE);
	}

	@Test
	@DisplayName("실패_공개 상태 변경 요청 version이 현재 문서와 다르면 충돌 응답을 반환한다")
	void updateDocumentVisibilityReturnsConflictWhenVersionMismatch() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document document = saveDocument(workspace.getId(), null, "공개 상태 대상", "00000000000000000001");

		var result = mockMvc.perform(patch("/v1/documents/{documentId}/visibility", document.getId())
			.contentType("application/json")
			.header("X-User-Id", "user-123")
			.content("""
				{
				  "visibility": "PUBLIC",
				  "version": 1
				}
				"""));

		assertErrorEnvelope(result, "CONFLICT", 9005, "요청이 현재 리소스 상태와 충돌합니다.");
	}

	@Test
	@DisplayName("실패_허용되지 않은 공개 상태값이면 유효성 검사 오류를 반환한다")
	void updateDocumentVisibilityReturnsValidationErrorWhenVisibilityInvalid() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document document = saveDocument(workspace.getId(), null, "공개 상태 대상", "00000000000000000001");

		var result = mockMvc.perform(patch("/v1/documents/{documentId}/visibility", document.getId())
			.contentType("application/json")
			.header("X-User-Id", "user-123")
			.content("""
				{
				  "visibility": "INTERNAL",
				  "version": 0
				}
				"""));

		assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
	}

	@Test
	@DisplayName("성공_문서 삭제 API는 하위 문서와 각 문서의 소속 블록까지 hard delete 처리한다")
	void deleteDocumentHardDeletesDescendantDocumentsAndBlocks() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document targetDocument = saveDocument(workspace.getId(), null, "삭제 대상 문서", "00000000000000000001");
		Document childDocument = saveDocument(workspace.getId(), targetDocument.getId(), "하위 문서",
			"00000000000000000002");
		Document otherDocument = saveDocument(workspace.getId(), null, "다른 문서", "00000000000000000002");

		Block targetRootBlock = saveBlock(targetDocument.getId(), null, "대상 루트 블록", "000000000001000000000000");
		Block targetChildBlock = saveBlock(targetDocument.getId(), targetRootBlock.getId(), "대상 자식 블록",
			"000000000001I00000000000");
		Block childDocumentBlock = saveBlock(childDocument.getId(), null, "하위 문서 블록", "000000000001000000000000");
		Block otherDocumentBlock = saveBlock(otherDocument.getId(), null, "다른 문서 블록", "000000000001000000000000");
		documentDeleteSqlCounter.reset();

		mockMvc.perform(delete("/v1/documents/{documentId}", targetDocument.getId())
				.header("X-User-Id", "user-123"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value(200));

		assertThat(documentRepository.findById(targetDocument.getId())).isEmpty();
		assertThat(documentRepository.findById(childDocument.getId())).isEmpty();
		assertThat(blockRepository.findById(targetRootBlock.getId())).isEmpty();
		assertThat(blockRepository.findById(targetChildBlock.getId())).isEmpty();
		assertThat(blockRepository.findById(childDocumentBlock.getId())).isEmpty();
		assertThat(blockRepository.findById(otherDocumentBlock.getId()))
			.get()
			.extracting(Block::getId)
			.isEqualTo(otherDocumentBlock.getId());
	}

	@Test
	@DisplayName("성공_문서 삭제 후 같은 문서를 단건 조회하면 문서 없음 응답을 반환한다")
	void getDocumentReturnsNotFoundAfterDelete() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document document = saveDocument(workspace.getId(), null, "삭제 대상 문서", "00000000000000000001");
		saveBlock(document.getId(), null, "대상 블록", "000000000001000000000000");

		mockMvc.perform(delete("/v1/documents/{documentId}", document.getId())
				.header("X-User-Id", "user-123"))
			.andExpect(status().isOk());

		var result = mockMvc.perform(get("/v1/documents/{documentId}", document.getId()));

		assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_존재하지 않는 문서를 삭제하면 문서 없음 응답을 반환한다")
	void deleteDocumentReturnsNotFoundWhenDocumentMissing() throws Exception {
		var result = mockMvc.perform(delete("/v1/documents/{documentId}", UUID.randomUUID())
			.header("X-User-Id", "user-123"));

		assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("성공_문서 휴지통 이동 API는 대상 문서를 soft delete 처리한다")
	void trashDocumentSoftDeletesDocument() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document targetDocument = saveDocument(workspace.getId(), null, "휴지통 대상 문서", "00000000000000000001");

		mockMvc.perform(patch("/v1/documents/{documentId}/trash", targetDocument.getId())
				.header("X-User-Id", "user-123"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value(200));

		assertThat(documentRepository.findById(targetDocument.getId())).get()
			.extracting(Document::getDeletedAt)
			.isNotNull();
	}

	@Test
	@DisplayName("성공_문서 휴지통 이동 API는 하위 문서와 각 문서의 소속 블록까지 soft delete 처리한다")
	void trashDocumentSoftDeletesDescendantDocumentsAndBlocks() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document targetDocument = saveDocument(workspace.getId(), null, "삭제 대상 문서", "00000000000000000001");
		Document childDocument = saveDocument(workspace.getId(), targetDocument.getId(), "하위 문서",
			"00000000000000000002");
		Document otherDocument = saveDocument(workspace.getId(), null, "다른 문서", "00000000000000000002");

		Block targetRootBlock = saveBlock(targetDocument.getId(), null, "대상 루트 블록", "000000000001000000000000");
		Block targetChildBlock = saveBlock(targetDocument.getId(), targetRootBlock.getId(), "대상 자식 블록",
			"000000000001I00000000000");
		Block childDocumentBlock = saveBlock(childDocument.getId(), null, "하위 문서 블록", "000000000001000000000000");
		Block otherDocumentBlock = saveBlock(otherDocument.getId(), null, "다른 문서 블록", "000000000001000000000000");
		documentDeleteSqlCounter.reset();

		mockMvc.perform(patch("/v1/documents/{documentId}/trash", targetDocument.getId())
				.header("X-User-Id", "user-123"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value(200));

		Document deletedDocument = documentRepository.findById(targetDocument.getId()).orElseThrow();
		Document deletedChildDocument = documentRepository.findById(childDocument.getId()).orElseThrow();
		assertThat(deletedDocument.getDeletedAt()).isNotNull();
		assertThat(deletedChildDocument.getDeletedAt()).isNotNull();

		Block deletedRootBlock = blockRepository.findById(targetRootBlock.getId()).orElseThrow();
		Block deletedChildBlock = blockRepository.findById(targetChildBlock.getId()).orElseThrow();
		Block deletedDescendantDocumentBlock = blockRepository.findById(childDocumentBlock.getId()).orElseThrow();
		Block survivedOtherBlock = blockRepository.findById(otherDocumentBlock.getId()).orElseThrow();

		assertThat(deletedRootBlock.getDeletedAt()).isNotNull();
		assertThat(deletedChildBlock.getDeletedAt()).isNotNull();
		assertThat(deletedDescendantDocumentBlock.getDeletedAt()).isNotNull();
		assertThat(survivedOtherBlock.getDeletedAt()).isNull();
		assertThat(documentDeleteSqlCounter.documentSoftDeleteUpdateCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("실패_존재하지 않는 문서를 휴지통 이동하면 문서 없음 응답을 반환한다")
	void trashDocumentReturnsNotFoundWhenDocumentMissing() throws Exception {
		var result = mockMvc.perform(patch("/v1/documents/{documentId}/trash", UUID.randomUUID())
			.header("X-User-Id", "user-123"));

		assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_이미 휴지통 상태인 문서를 다시 휴지통 이동하면 문서 없음 응답을 반환한다")
	void trashDocumentReturnsNotFoundWhenDocumentAlreadyTrashed() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document deletedDocument = saveDeletedDocument(workspace.getId(), "이미 삭제된 문서", "00000000000000000001");

		var result = mockMvc.perform(patch("/v1/documents/{documentId}/trash", deletedDocument.getId())
			.header("X-User-Id", "user-123"));

		assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("성공_문서 복구 API는 삭제 문서와 해당 문서 소속 삭제 블록을 함께 복구한다")
	void restoreDocumentRestoresDocumentAndOwnedDeletedBlocks() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document deletedDocument = saveDeletedDocument(
			workspace.getId(),
			"복구 대상 문서",
			"00000000000000000001",
			LocalDateTime.now().minusMinutes(1)
		);
		Document otherDocument = saveDocument(workspace.getId(), null, "다른 문서", "00000000000000000002");

		Block deletedTargetBlock = saveDeletedBlock(
			deletedDocument.getId(),
			null,
			"복구 대상 블록",
			"000000000001000000000000",
			LocalDateTime.now().minusMinutes(1)
		);
		Block activeTargetBlock = saveBlock(deletedDocument.getId(), null, "활성 블록", "000000000002000000000000");
		Block deletedOtherBlock = saveDeletedBlock(
			otherDocument.getId(),
			null,
			"다른 문서 블록",
			"000000000001000000000000",
			LocalDateTime.now().minusMinutes(1)
		);

		mockMvc.perform(post("/v1/documents/{documentId}/restore", deletedDocument.getId())
				.header("X-User-Id", "user-123"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value(200));

		Document restoredDocument = documentRepository.findById(deletedDocument.getId()).orElseThrow();
		Block restoredTargetBlock = blockRepository.findById(deletedTargetBlock.getId()).orElseThrow();
		Block survivedActiveTargetBlock = blockRepository.findById(activeTargetBlock.getId()).orElseThrow();
		Block survivedOtherBlock = blockRepository.findById(deletedOtherBlock.getId()).orElseThrow();

		assertThat(restoredDocument.getDeletedAt()).isNull();
		assertThat(restoredTargetBlock.getDeletedAt()).isNull();
		assertThat(survivedActiveTargetBlock.getDeletedAt()).isNull();
		assertThat(survivedOtherBlock.getDeletedAt()).isNotNull();
	}

	@Test
	@DisplayName("실패_존재하지 않는 문서 복구 요청은 문서 없음 응답을 반환한다")
	void restoreDocumentReturnsNotFoundWhenDocumentMissing() throws Exception {
		var result = mockMvc.perform(post("/v1/documents/{documentId}/restore", UUID.randomUUID())
			.header("X-User-Id", "user-123"));

		assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_이미 활성 상태인 문서 복구 요청은 문서 없음 응답을 반환한다")
	void restoreDocumentReturnsNotFoundWhenDocumentAlreadyActive() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document activeDocument = saveDocument(workspace.getId(), null, "활성 문서", "00000000000000000001");

		var result = mockMvc.perform(post("/v1/documents/{documentId}/restore", activeDocument.getId())
			.header("X-User-Id", "user-123"));

		assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_같은 워크스페이스에 같은 제목 활성 문서가 있으면 문서 복구는 충돌 응답을 반환한다")
	void restoreDocumentReturnsConflictWhenTitleAlreadyExists() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document deletedDocument = saveDeletedDocument(workspace.getId(), "복구 대상 문서", "00000000000000000001");
		saveDocument(workspace.getId(), null, "복구 대상 문서", "00000000000000000002");

		var result = mockMvc.perform(post("/v1/documents/{documentId}/restore", deletedDocument.getId())
			.header("X-User-Id", "user-123"));

		assertErrorEnvelope(result, "CONFLICT", 9005, "요청이 현재 리소스 상태와 충돌합니다.");
	}

	@Test
	@DisplayName("실패_휴지통 보관 시간 5분이 지난 문서 복구 요청은 문서 없음 응답을 반환한다")
	void restoreDocumentReturnsNotFoundWhenTrashRetentionExpired() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document expiredDeletedDocument = saveDeletedDocument(
			workspace.getId(),
			"만료된 삭제 문서",
			"00000000000000000001",
			LocalDateTime.now().minusMinutes(6)
		);

		var result = mockMvc.perform(post("/v1/documents/{documentId}/restore", expiredDeletedDocument.getId())
			.header("X-User-Id", "user-123"));

		assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("성공_휴지통 보관 시간이 지난 문서는 자동 영구 삭제 시 하위 문서와 블록까지 함께 삭제한다")
	void purgeExpiredTrashDeletesDescendantDocumentsAndBlocks() {
		Workspace workspace = workspace("Docs Root");
		Document trashRoot = saveDeletedDocument(
			workspace.getId(),
			"만료된 삭제 문서",
			"00000000000000000001",
			LocalDateTime.now().minusMinutes(6)
		);
		Document trashChild = documentRepository.save(Document.builder()
			.id(UUID.randomUUID())
			.workspace(workspaceRepository.getReferenceById(workspace.getId()))
			.parent(documentRepository.getReferenceById(trashRoot.getId()))
			.title("만료된 하위 문서")
			.sortKey("00000000000000000002")
			.deletedAt(LocalDateTime.now().minusMinutes(6))
			.build());
		Block rootBlock = saveDeletedBlock(
			trashRoot.getId(),
			null,
			"루트 삭제 블록",
			"000000000001000000000000",
			LocalDateTime.now().minusMinutes(6)
		);
		Block childBlock = saveDeletedBlock(
			trashChild.getId(),
			null,
			"하위 삭제 블록",
			"000000000001000000000000",
			LocalDateTime.now().minusMinutes(6)
		);
		Document safeDocument = saveDeletedDocument(
			workspace.getId(),
			"미만료 삭제 문서",
			"00000000000000000003",
			LocalDateTime.now().minusMinutes(4).minusSeconds(59)
		);
		Block safeBlock = saveDeletedBlock(
			safeDocument.getId(),
			null,
			"미만료 블록",
			"000000000001000000000000",
			LocalDateTime.now().minusMinutes(4).minusSeconds(59)
		);

		documentService.purgeExpiredTrash();

		assertThat(documentRepository.findById(trashRoot.getId())).isEmpty();
		assertThat(documentRepository.findById(trashChild.getId())).isEmpty();
		assertThat(blockRepository.findById(rootBlock.getId())).isEmpty();
		assertThat(blockRepository.findById(childBlock.getId())).isEmpty();
		assertThat(documentRepository.findById(safeDocument.getId())).isPresent();
		assertThat(blockRepository.findById(safeBlock.getId())).isPresent();
	}

	@Test
	@DisplayName("성공_문서 move API는 parentId와 sortKey와 updatedBy와 updatedAt을 반영한다")
	void moveDocumentPersistsParentSortKeyAndAuditFields() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document rootA = saveDocument(workspace.getId(), null, "루트 A", "000000000001000000000000");
		Document rootB = saveDocument(workspace.getId(), null, "루트 B", "000000000002000000000000");
		Document movedDocument = saveDocument(workspace.getId(), rootA.getId(), "이동 대상", "000000000001000000000000",
			null, null, "user-123", "user-123");
		LocalDateTime previousUpdatedAt = movedDocument.getUpdatedAt();

		mockMvc.perform(post("/v1/documents/{documentId}/move", movedDocument.getId())
				.contentType("application/json")
				.header("X-User-Id", "user-456")
				.content("""
					{
					  "targetParentId": "%s",
					  "afterDocumentId": null,
					  "beforeDocumentId": null
					}
					""".formatted(rootB.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value(200));

		Document updatedDocument = documentRepository.findById(movedDocument.getId()).orElseThrow();
		assertThat(updatedDocument.getParentId()).isEqualTo(rootB.getId());
		assertThat(updatedDocument.getSortKey()).isNotBlank();
		assertThat(updatedDocument.getUpdatedBy()).isEqualTo("user-456");
		assertThat(updatedDocument.getUpdatedAt()).isAfterOrEqualTo(previousUpdatedAt);
	}

	@Test
	@DisplayName("성공_같은 부모 내 reorder 결과가 문서 목록 조회 순서에 반영된다")
	void moveDocumentReordersWithinSameParentInDocumentList() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document parent = saveDocument(workspace.getId(), null, "부모 문서", "000000000001000000000000");
		Document first = saveDocument(workspace.getId(), parent.getId(), "첫 번째", "000000000002000000000000");
		Document second = saveDocument(workspace.getId(), parent.getId(), "두 번째", "000000000006000000000000");
		Document third = saveDocument(workspace.getId(), parent.getId(), "세 번째", "00000000000A000000000000");

		mockMvc.perform(post("/v1/documents/{documentId}/move", third.getId())
				.contentType("application/json")
				.header("X-User-Id", "user-456")
				.content("""
					{
					  "targetParentId": "%s",
					  "afterDocumentId": "%s",
					  "beforeDocumentId": "%s"
					}
					""".formatted(parent.getId(), first.getId(), second.getId())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/v1/workspaces/{workspaceId}/documents", workspace.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(4))
			.andExpect(jsonPath("$.data[0].title").value("부모 문서"))
			.andExpect(jsonPath("$.data[1].title").value("첫 번째"))
			.andExpect(jsonPath("$.data[2].title").value("세 번째"))
			.andExpect(jsonPath("$.data[3].title").value("두 번째"));
	}

	@Test
	@DisplayName("성공_다른 부모로 이동한 결과가 문서 목록 조회에 반영된다")
	void moveDocumentToAnotherParentReflectsInDocumentList() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document rootA = saveDocument(workspace.getId(), null, "루트 A", "000000000004000000000000");
		Document rootB = saveDocument(workspace.getId(), null, "루트 B", "000000000001000000000000");
		Document childA = saveDocument(workspace.getId(), rootA.getId(), "A의 자식", "000000000008000000000000");
		Document childB = saveDocument(workspace.getId(), rootB.getId(), "B의 자식", "000000000002000000000000");

		mockMvc.perform(post("/v1/documents/{documentId}/move", childA.getId())
				.contentType("application/json")
				.header("X-User-Id", "user-456")
				.content("""
					{
					  "targetParentId": "%s",
					  "afterDocumentId": "%s",
					  "beforeDocumentId": null
					}
					""".formatted(rootB.getId(), childB.getId())))
			.andExpect(status().isOk());

		Document movedDocument = documentRepository.findById(childA.getId()).orElseThrow();
		assertThat(movedDocument.getParentId()).isEqualTo(rootB.getId());

		mockMvc.perform(get("/v1/workspaces/{workspaceId}/documents", workspace.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(4))
			.andExpect(jsonPath("$.data[0].title").value("루트 B"))
			.andExpect(jsonPath("$.data[1].title").value("B의 자식"))
			.andExpect(jsonPath("$.data[2].title").value("A의 자식"))
			.andExpect(jsonPath("$.data[2].parentId").value(rootB.getId().toString()))
			.andExpect(jsonPath("$.data[3].title").value("루트 A"));
	}

	@Test
	@DisplayName("실패_존재하지 않는 문서 move 요청은 문서 없음 응답을 반환한다")
	void moveDocumentReturnsNotFoundWhenDocumentMissing() throws Exception {
		var result = mockMvc.perform(post("/v1/documents/{documentId}/move", UUID.randomUUID())
			.contentType("application/json")
			.header("X-User-Id", "user-123")
			.content("""
				{
				  "targetParentId": null,
				  "afterDocumentId": null,
				  "beforeDocumentId": null
				}
				"""));

		assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_삭제된 문서 move 요청은 문서 없음 응답을 반환한다")
	void moveDocumentReturnsNotFoundWhenDeletedDocument() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document deletedDocument = saveDeletedDocument(workspace.getId(), "삭제된 문서", "00000000000000000001");

		var result = mockMvc.perform(post("/v1/documents/{documentId}/move", deletedDocument.getId())
			.contentType("application/json")
			.header("X-User-Id", "user-123")
			.content("""
				{
				  "targetParentId": null,
				  "afterDocumentId": null,
				  "beforeDocumentId": null
				}
				"""));

		assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_자기 자신을 부모로 지정한 문서 move 요청은 잘못된 요청 응답을 반환한다")
	void moveDocumentReturnsBadRequestWhenParentIsSelf() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document document = saveDocument(workspace.getId(), null, "루트 문서", "00000000000000000001");

		var result = mockMvc.perform(post("/v1/documents/{documentId}/move", document.getId())
			.contentType("application/json")
			.header("X-User-Id", "user-123")
			.content("""
				{
				  "targetParentId": "%s",
				  "afterDocumentId": null,
				  "beforeDocumentId": null
				}
				""".formatted(document.getId())));

		assertErrorEnvelope(result, "BAD_REQUEST", 9015, "잘못된 요청입니다.");
	}

	@Test
	@DisplayName("실패_하위 문서를 부모로 지정한 문서 move 요청은 잘못된 요청 응답을 반환한다")
	void moveDocumentReturnsBadRequestWhenCycleDetected() throws Exception {
		Workspace workspace = workspace("Docs Root");
		Document rootDocument = saveDocument(workspace.getId(), null, "루트 문서", "00000000000000000001");
		Document childDocument = saveDocument(workspace.getId(), rootDocument.getId(), "하위 문서", "00000000000000000001");

		var result = mockMvc.perform(post("/v1/documents/{documentId}/move", rootDocument.getId())
			.contentType("application/json")
			.header("X-User-Id", "user-123")
			.content("""
				{
				  "targetParentId": "%s",
				  "afterDocumentId": null,
				  "beforeDocumentId": null
				}
				""".formatted(childDocument.getId())));

		assertErrorEnvelope(result, "BAD_REQUEST", 9015, "잘못된 요청입니다.");
	}

	private void assertErrorEnvelope(ResultActions result, String httpStatus, int code, String message) throws
		Exception {
		result.andExpect(status().is(org.springframework.http.HttpStatus.valueOf(httpStatus).value()))
			.andExpect(jsonPath("$.httpStatus").value(httpStatus))
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.message").value(message))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	private Workspace workspace(String name) {
		return workspaceRepository.save(Workspace.builder()
			.id(UUID.randomUUID())
			.name(name)
			.build());
	}

	private Document saveDocument(UUID workspaceId, UUID parentId, String title, String sortKey) {
		return saveDocument(workspaceId, parentId, title, sortKey, null, null, null, null);
	}

	private Document saveDocument(
		UUID workspaceId,
		UUID parentId,
		String title,
		String sortKey,
		String iconJson,
		String coverJson,
		String createdBy,
		String updatedBy
	) {
		return documentRepository.save(Document.builder()
			.id(UUID.randomUUID())
			.workspace(workspaceRepository.getReferenceById(workspaceId))
			.parent(parentId == null ? null : documentRepository.getReferenceById(parentId))
			.title(title)
			.sortKey(sortKey)
			.iconJson(iconJson)
			.coverJson(coverJson)
			.createdBy(createdBy)
			.updatedBy(updatedBy)
			.build());
	}

	private Document saveDeletedDocument(UUID workspaceId, String title, String sortKey) {
		return saveDeletedDocument(workspaceId, title, sortKey, LocalDateTime.now().minusMinutes(1));
	}

	private Document saveDeletedDocument(UUID workspaceId, String title, String sortKey, LocalDateTime deletedAt) {
		return documentRepository.save(Document.builder()
			.id(UUID.randomUUID())
			.workspace(workspaceRepository.getReferenceById(workspaceId))
			.title(title)
			.sortKey(sortKey)
			.deletedAt(deletedAt)
			.build());
	}

	private Block saveBlock(UUID documentId, UUID parentId, String content, String sortKey) {
		return blockRepository.save(Block.builder()
			.id(UUID.randomUUID())
			.document(documentRepository.getReferenceById(documentId))
			.parent(parentId == null ? null : blockRepository.getReferenceById(parentId))
			.type(BlockType.TEXT)
			.content(toContent(content))
			.sortKey(sortKey)
			.createdBy("user-123")
			.updatedBy("user-123")
			.build());
	}

	private Block saveDeletedBlock(UUID documentId, UUID parentId, String content, String sortKey) {
		return saveDeletedBlock(documentId, parentId, content, sortKey, LocalDateTime.now().minusMinutes(1));
	}

	private Block saveDeletedBlock(
		UUID documentId,
		UUID parentId,
		String content,
		String sortKey,
		LocalDateTime deletedAt
	) {
		return blockRepository.save(Block.builder()
			.id(UUID.randomUUID())
			.document(documentRepository.getReferenceById(documentId))
			.parent(parentId == null ? null : blockRepository.getReferenceById(parentId))
			.type(BlockType.TEXT)
			.content(toContent(content))
			.sortKey(sortKey)
			.createdBy("user-123")
			.updatedBy("user-123")
			.deletedAt(deletedAt)
			.build());
	}

	private String toContent(String content) {
		return "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"%s\",\"marks\":[]}]}".formatted(
			content);
	}

	@TestConfiguration
	static class DocumentDeleteSqlCounterTestConfig {

		@Bean
		DocumentDeleteSqlCounter documentDeleteSqlCounter() {
			return new DocumentDeleteSqlCounter();
		}

		@Bean
		HibernatePropertiesCustomizer documentDeleteSqlCounterCustomizer(DocumentDeleteSqlCounter counter) {
			return properties -> properties.put("hibernate.session_factory.statement_inspector", counter);
		}
	}

	static class DocumentDeleteSqlCounter implements StatementInspector {

		private final AtomicInteger documentSoftDeleteUpdateCount = new AtomicInteger();

		@Override
		public String inspect(String sql) {
			String normalizedSql = sql.toLowerCase(Locale.ROOT);
			if (normalizedSql.contains("update documents") && normalizedSql.contains("deleted_at")) {
				documentSoftDeleteUpdateCount.incrementAndGet();
			}
			return sql;
		}

		void reset() {
			documentSoftDeleteUpdateCount.set(0);
		}

		int documentSoftDeleteUpdateCount() {
			return documentSoftDeleteUpdateCount.get();
		}
	}
}
