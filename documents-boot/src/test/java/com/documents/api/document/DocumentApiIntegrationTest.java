package com.documents.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.documents.domain.Block;
import com.documents.domain.BlockType;
import com.documents.domain.Document;
import com.documents.domain.DocumentVisibility;
import com.documents.repository.BlockRepository;
import com.documents.repository.DocumentRepository;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Document API 통합 검증")
class DocumentApiIntegrationTest {

	private static final String USER_ID_HEADER = "X-User-Id";
	private static final String USER_ID = "user-123";
	private static final String OTHER_USER_ID = "user-999";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private DocumentRepository documentRepository;

	@Autowired
	private BlockRepository blockRepository;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context)
			.defaultRequest(get("/").header(USER_ID_HEADER, USER_ID))
			.build();
		blockRepository.deleteAll();
		documentRepository.deleteAll();
	}

	@Test
	@DisplayName("성공_문서 생성 API는 현재 사용자 소유 루트 문서를 저장하고 응답한다")
	void createDocumentReturnsCreatedEnvelope() throws Exception {
		mockMvc.perform(post("/documents")
				.contentType("application/json")
				.header(USER_ID_HEADER, USER_ID)
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
			.andExpect(jsonPath("$.code").value(201))
			.andExpect(jsonPath("$.data.parentId").doesNotExist())
			.andExpect(jsonPath("$.data.title").value("프로젝트 개요"))
			.andExpect(jsonPath("$.data.sortKey").value("000000000001000000000000"))
			.andExpect(jsonPath("$.data.icon.type").value("emoji"))
			.andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
			.andExpect(jsonPath("$.data.createdBy").value(USER_ID))
			.andExpect(jsonPath("$.data.version").value(0));

		Document savedDocument = documentRepository.findAll().get(0);
		assertThat(savedDocument.getParentId()).isNull();
		assertThat(savedDocument.getTitle()).isEqualTo("프로젝트 개요");
		assertThat(savedDocument.getSortKey()).isEqualTo("000000000001000000000000");
		assertThat(savedDocument.getIconJson()).isEqualTo("{\"type\":\"emoji\",\"value\":\"📄\"}");
		assertThat(savedDocument.getCreatedBy()).isEqualTo(USER_ID);
		assertThat(savedDocument.getUpdatedBy()).isEqualTo(USER_ID);
	}

	@Test
	@DisplayName("성공_내 문서 목록 조회 API는 현재 사용자 활성 문서만 반환한다")
	void getDocumentsReturnsOnlyCurrentUserActiveDocuments() throws Exception {
		Document rootDocument = saveDocument(USER_ID, null, "루트 문서", "00000000000000000001");
		saveDocument(USER_ID, rootDocument.getId(), "하위 문서", "00000000000000000002");
		saveDeletedDocument(USER_ID, "삭제된 문서", "00000000000000000003", LocalDateTime.now().minusMinutes(1));
		saveDocument(OTHER_USER_ID, null, "다른 사용자 문서", "00000000000000000004");

		mockMvc.perform(get("/documents").header(USER_ID_HEADER, USER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].title").value("루트 문서"))
			.andExpect(jsonPath("$.data[1].title").value("하위 문서"));
	}

	@Test
	@DisplayName("성공_내 휴지통 문서 목록 조회 API는 현재 사용자 삭제 문서만 반환한다")
	void getTrashDocumentsReturnsOnlyCurrentUserDeletedDocuments() throws Exception {
		Document newerDeletedDocument = saveDeletedDocument(
			USER_ID,
			"최근 삭제 문서",
			"00000000000000000001",
			LocalDateTime.of(2026, 3, 25, 12, 0, 0)
		);
		Document olderDeletedDocument = saveDeletedDocument(
			USER_ID,
			"이전 삭제 문서",
			"00000000000000000002",
			LocalDateTime.of(2026, 3, 25, 11, 55, 0)
		);
		olderDeletedDocument.setParent(newerDeletedDocument);
		documentRepository.save(olderDeletedDocument);
		saveDocument(USER_ID, null, "활성 문서", "00000000000000000003");
		saveDeletedDocument(OTHER_USER_ID, "다른 사용자 삭제 문서", "00000000000000000004",
			LocalDateTime.of(2026, 3, 25, 12, 1, 0));

		mockMvc.perform(get("/documents/trash").header(USER_ID_HEADER, USER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].documentId").value(newerDeletedDocument.getId().toString()))
			.andExpect(jsonPath("$.data[0].title").value("최근 삭제 문서"))
			.andExpect(jsonPath("$.data[0].purgeAt").value("2026-03-25T12:05:00"))
			.andExpect(jsonPath("$.data[1].documentId").value(olderDeletedDocument.getId().toString()))
			.andExpect(jsonPath("$.data[1].parentId").value(newerDeletedDocument.getId().toString()));
	}

	@Test
	@DisplayName("실패_다른 사용자 소유 부모 문서로 생성 요청하면 잘못된 요청을 반환한다")
	void createDocumentReturnsBadRequestWhenParentBelongsToOtherUser() throws Exception {
		Document parentDocument = saveDocument(OTHER_USER_ID, null, "다른 사용자 문서", "00000000000000000001");

		var result = mockMvc.perform(post("/documents")
			.contentType("application/json")
			.header(USER_ID_HEADER, USER_ID)
			.content("""
				{
				  "parentId": "%s",
				  "title": "프로젝트 개요"
				}
				""".formatted(parentDocument.getId())));

		assertErrorEnvelope(result, "BAD_REQUEST", 9015, "잘못된 요청입니다.");
	}

	@Test
	@DisplayName("성공_문서 단건 조회 API는 문서 응답을 반환한다")
	void getDocumentReturnsEnvelope() throws Exception {
		Document document = saveDocument(USER_ID, null, "프로젝트 개요", "00000000000000000007");
		document.setIconJson("{\"type\":\"emoji\",\"value\":\"📄\"}");
		documentRepository.save(document);

		mockMvc.perform(get("/documents/{documentId}", document.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(document.getId().toString()))
			.andExpect(jsonPath("$.data.title").value("프로젝트 개요"))
			.andExpect(jsonPath("$.data.sortKey").value("00000000000000000007"))
			.andExpect(jsonPath("$.data.icon.value").value("📄"));
	}

	@Test
	@DisplayName("성공_문서 수정 API는 제목과 커버를 갱신한다")
	void updateDocumentReturnsUpdatedEnvelope() throws Exception {
		Document document = saveDocument(USER_ID, null, "기존 제목", "00000000000000000001");

		mockMvc.perform(patch("/documents/{documentId}", document.getId())
				.contentType("application/json")
				.header(USER_ID_HEADER, USER_ID)
				.content("""
					{
					  "title": "수정된 제목",
					  "version": 0,
					  "cover": {
					    "type": "image",
					    "value": "cover-2"
					  }
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.title").value("수정된 제목"))
			.andExpect(jsonPath("$.data.cover.value").value("cover-2"));
	}

	@Test
	@DisplayName("성공_문서 공개 상태 수정 API는 visibility와 version을 갱신한다")
	void updateDocumentVisibilityReturnsUpdatedEnvelope() throws Exception {
		Document document = saveDocument(USER_ID, null, "공개 상태 문서", "00000000000000000001");

		mockMvc.perform(patch("/documents/{documentId}/visibility", document.getId())
				.contentType("application/json")
				.header(USER_ID_HEADER, USER_ID)
				.content("""
					{
					  "visibility": "PUBLIC",
					  "version": 0
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
			.andExpect(jsonPath("$.data.version").value(1));
	}

	@Test
	@DisplayName("성공_문서 이동 API는 같은 사용자 소유 부모 아래로 이동한다")
	void moveDocumentMovesWithinCurrentUserDocuments() throws Exception {
		Document rootDocument = saveDocument(USER_ID, null, "루트 문서", "00000000000000000001");
		Document movingDocument = saveDocument(USER_ID, null, "이동 대상", "00000000000000000002");

		mockMvc.perform(post("/documents/{documentId}/move", movingDocument.getId())
				.contentType("application/json")
				.header(USER_ID_HEADER, USER_ID)
				.content("""
					{
					  "targetParentId": "%s",
					  "afterDocumentId": null,
					  "beforeDocumentId": null
					}
					""".formatted(rootDocument.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true));

		Document reloaded = documentRepository.findByIdAndDeletedAtIsNull(movingDocument.getId()).orElseThrow();
		assertThat(reloaded.getParentId()).isEqualTo(rootDocument.getId());
	}

	@Test
	@DisplayName("성공_문서 휴지통 이동 후 복구 API는 deletedAt을 다시 null로 만든다")
	void trashAndRestoreDocumentChangesDeletedAt() throws Exception {
		Document document = saveDocument(USER_ID, null, "복구 대상", "00000000000000000001");
		saveBlock(document.getId(), null, "루트 블록", "000000000001000000000000");

		mockMvc.perform(patch("/documents/{documentId}/trash", document.getId())
				.header(USER_ID_HEADER, USER_ID))
			.andExpect(status().isOk());

		Document trashed = documentRepository.findById(document.getId()).orElseThrow();
		assertThat(trashed.getDeletedAt()).isNotNull();

		mockMvc.perform(post("/documents/{documentId}/restore", document.getId())
				.header(USER_ID_HEADER, USER_ID))
			.andExpect(status().isOk());

		Document restored = documentRepository.findById(document.getId()).orElseThrow();
		assertThat(restored.getDeletedAt()).isNull();
	}

	@Test
	@DisplayName("성공_문서 삭제 API는 문서와 소속 블록을 물리 삭제한다")
	void deleteDocumentRemovesDocumentAndBlocks() throws Exception {
		Document document = saveDocument(USER_ID, null, "삭제 대상", "00000000000000000001");
		saveBlock(document.getId(), null, "루트 블록", "000000000001000000000000");

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.delete("/documents/{documentId}", document.getId())
				.header(USER_ID_HEADER, USER_ID))
			.andExpect(status().isOk());

		assertThat(documentRepository.findById(document.getId())).isEmpty();
		assertThat(blockRepository.findActiveByDocumentIdOrderBySortKey(document.getId())).isEmpty();
	}

	private void assertErrorEnvelope(ResultActions result, String httpStatus, int code, String message) throws Exception {
		result.andExpect(status().is(org.springframework.http.HttpStatus.valueOf(httpStatus).value()))
			.andExpect(jsonPath("$.httpStatus").value(httpStatus))
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.message").value(message))
			.andExpect(jsonPath("$.code").value(code));
	}

	private Document saveDocument(String ownerId, UUID parentId, String title, String sortKey) {
		return documentRepository.save(Document.builder()
			.id(UUID.randomUUID())
			.parent(parentId == null ? null : documentRepository.getReferenceById(parentId))
			.title(title)
			.sortKey(sortKey)
			.visibility(DocumentVisibility.PRIVATE)
			.createdBy(ownerId)
			.updatedBy(ownerId)
			.build());
	}

	private Document saveDeletedDocument(String ownerId, String title, String sortKey, LocalDateTime deletedAt) {
		return documentRepository.save(Document.builder()
			.id(UUID.randomUUID())
			.title(title)
			.sortKey(sortKey)
			.visibility(DocumentVisibility.PRIVATE)
			.createdBy(ownerId)
			.updatedBy(ownerId)
			.deletedAt(deletedAt)
			.build());
	}

	private Block saveBlock(UUID documentId, UUID parentId, String text, String sortKey) {
		return blockRepository.save(Block.builder()
			.id(UUID.randomUUID())
			.document(documentRepository.getReferenceById(documentId))
			.parent(parentId == null ? null : blockRepository.getReferenceById(parentId))
			.type(BlockType.TEXT)
			.content(toContent(text))
			.sortKey(sortKey)
			.createdBy(USER_ID)
			.updatedBy(USER_ID)
			.build());
	}

	private String toContent(String text) {
		return "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"%s\",\"marks\":[]}]}".formatted(text);
	}
}
