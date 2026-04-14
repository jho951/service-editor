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

import com.documents.api.auth.CurrentUserIdArgumentResolver;
import com.documents.api.block.BlockApiMapper;
import com.documents.api.block.support.BlockJsonCodec;
import com.documents.api.document.support.DocumentJsonCodec;
import com.documents.api.exception.GlobalExceptionHandler;
import com.documents.api.support.ApiResponseAssertions;
import com.documents.domain.Block;
import com.documents.domain.BlockType;
import com.documents.domain.Document;
import com.documents.domain.DocumentVisibility;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.service.BlockService;
import com.documents.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("Document 컨트롤러 빠른 검증")
class DocumentControllerWebMvcTest {

	private static final String USER_ID_HEADER = "X-User-Id";
	private static final String ACTOR_ID = "user-123";
	private static final String PROJECT_OVERVIEW_TITLE = "프로젝트 개요";
	private static final String UPDATED_PROJECT_OVERVIEW_TITLE = "수정된 프로젝트 개요";
	private static final String ROOT_DOCUMENT_TITLE = "루트 문서";
	private static final String CHILD_DOCUMENT_TITLE = "하위 문서";
	private static final String PARENT_DOCUMENT_TITLE = "부모 문서";
	private static final String ICON_DOC_JSON = "{\"type\":\"emoji\",\"value\":\"📄\"}";
	private static final String COVER_1_JSON = "{\"type\":\"image\",\"value\":\"cover-1\"}";
	private static final String COVER_2_JSON = "{\"type\":\"image\",\"value\":\"cover-2\"}";
	private static final String ROOT_BLOCK_CONTENT_JSON = "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"루트 블록\",\"marks\":[]}]}";
	private static final String CHILD_BLOCK_CONTENT_JSON = "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"자식 블록\",\"marks\":[]}]}";
	private static final LocalDateTime FIXTURE_TIME = LocalDateTime.of(2026, 3, 16, 0, 0);

	@Mock
	private BlockService blockService;

	@Mock
	private DocumentService documentService;

	private MockMvc mockMvc;

	private Document document(
		UUID id,
		UUID ownerMarker,
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
			.parent(parentId == null ? null : parentDocument(parentId, ownerMarker))
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

	private Document parentDocument(UUID documentId, UUID ownerMarker) {
		return Document.builder()
			.id(documentId)
			.title(PARENT_DOCUMENT_TITLE)
			.sortKey("00000000000000000001")
			.createdBy(ownerMarker.toString())
			.updatedBy(ownerMarker.toString())
			.build();
	}

	private Block block(
		UUID id,
		UUID documentId,
		UUID parentId,
		String sortKey,
		int version,
		String content
	) {
		Block block = Block.builder()
			.id(id)
			.document(Document.builder().id(documentId).title(ROOT_DOCUMENT_TITLE).createdBy(ACTOR_ID).updatedBy(ACTOR_ID).build())
			.parent(parentId == null ? null : Block.builder().id(parentId).build())
			.type(BlockType.TEXT)
			.sortKey(sortKey)
			.content(content)
			.createdBy(ACTOR_ID)
			.updatedBy(ACTOR_ID)
			.build();
		block.setCreatedAt(FIXTURE_TIME);
		block.setUpdatedAt(FIXTURE_TIME);
		block.setVersion(version);
		return block;
	}

	@BeforeEach
	void setUp() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		BlockJsonCodec blockJsonCodec = new BlockJsonCodec(new ObjectMapper());

		mockMvc = MockMvcBuilders.standaloneSetup(new DocumentController(
				blockService,
				new BlockApiMapper(blockJsonCodec),
				documentService,
				new DocumentApiMapper(new DocumentJsonCodec(new ObjectMapper()))
			))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
				.setValidator(validator)
				.build();
	}

	@Test
	@DisplayName("성공_문서 블록 목록 조회 요청에 대해 활성 블록 전체를 반환한다")
	void getBlocksReturnsAllActiveBlocks() throws Exception {
		UUID documentId = UUID.randomUUID();
		UUID rootBlockId = UUID.randomUUID();
		UUID childBlockId = UUID.randomUUID();

		when(blockService.getAllByDocumentId(documentId)).thenReturn(List.of(
			block(rootBlockId, documentId, null, "000000000001000000000000", 0, ROOT_BLOCK_CONTENT_JSON),
			block(childBlockId, documentId, rootBlockId, "000000000001I00000000000", 1, CHILD_BLOCK_CONTENT_JSON)
		));

		mockMvc.perform(get("/documents/{documentId}/blocks", documentId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].id").value(rootBlockId.toString()))
			.andExpect(jsonPath("$.data[0].content.format").value("rich_text"))
			.andExpect(jsonPath("$.data[0].content.segments[0].text").value("루트 블록"))
			.andExpect(jsonPath("$.data[1].parentId").value(rootBlockId.toString()))
			.andExpect(jsonPath("$.data[1].content.format").value("rich_text"))
			.andExpect(jsonPath("$.data[1].content.segments[0].text").value("자식 블록"));
	}

	@Test
	@DisplayName("성공_내 휴지통 문서 목록 조회 요청에 대해 휴지통 문서 목록을 반환한다")
	void getTrashDocumentsReturnsTrashDocumentList() throws Exception {
		UUID deletedRootId = UUID.randomUUID();
		UUID deletedChildId = UUID.randomUUID();
		Document deletedRoot = document(
			deletedRootId,
			UUID.randomUUID(),
			null,
			"삭제된 루트 문서",
			ACTOR_ID,
			0,
			"00000000000000000001",
			null,
			null
		);
		deletedRoot.setDeletedAt(FIXTURE_TIME);
		Document deletedChild = document(
			deletedChildId,
			UUID.randomUUID(),
			deletedRootId,
			"삭제된 자식 문서",
			ACTOR_ID,
			0,
			"00000000000000000002",
			null,
			null
		);
		deletedChild.setDeletedAt(FIXTURE_TIME.minusMinutes(1));
		when(documentService.getTrashByUserId(ACTOR_ID)).thenReturn(List.of(deletedRoot, deletedChild));

		mockMvc.perform(get("/documents/trash").header(USER_ID_HEADER, ACTOR_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].documentId").value(deletedRootId.toString()))
			.andExpect(jsonPath("$.data[0].title").value("삭제된 루트 문서"))
			.andExpect(jsonPath("$.data[0].parentId").doesNotExist())
			.andExpect(jsonPath("$.data[0].deletedAt[0]").value(2026))
			.andExpect(jsonPath("$.data[0].deletedAt[1]").value(3))
			.andExpect(jsonPath("$.data[0].deletedAt[2]").value(16))
			.andExpect(jsonPath("$.data[0].deletedAt[3]").value(0))
			.andExpect(jsonPath("$.data[0].deletedAt[4]").value(0))
			.andExpect(jsonPath("$.data[0].purgeAt[0]").value(2026))
			.andExpect(jsonPath("$.data[0].purgeAt[1]").value(3))
			.andExpect(jsonPath("$.data[0].purgeAt[2]").value(16))
			.andExpect(jsonPath("$.data[0].purgeAt[3]").value(0))
			.andExpect(jsonPath("$.data[0].purgeAt[4]").value(5))
			.andExpect(jsonPath("$.data[1].documentId").value(deletedChildId.toString()))
			.andExpect(jsonPath("$.data[1].parentId").value(deletedRootId.toString()));
	}

	@Test
	@DisplayName("실패_인증 헤더가 없으면 휴지통 문서 목록 조회에서 인증 오류를 반환한다")
	void getTrashDocumentsRequiresUserHeader() throws Exception {
		var result = mockMvc.perform(get("/documents/trash"));

		ApiResponseAssertions.assertErrorEnvelope(result, "UNAUTHORIZED", 9001, "인증 정보가 없습니다.");
	}

	@Test
	@DisplayName("성공_문서 생성 요청에 대해 생성 응답을 반환한다")
	void createDocumentReturnsCreatedEnvelope() throws Exception {
		UUID documentId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();

		when(documentService.create(
			eq(parentId),
			eq(PROJECT_OVERVIEW_TITLE),
			eq(ICON_DOC_JSON),
			eq(COVER_1_JSON),
			eq(ACTOR_ID)
			)).thenReturn(document(documentId, UUID.randomUUID(), parentId, PROJECT_OVERVIEW_TITLE, ACTOR_ID, 0,
			"00000000000000000003",
			ICON_DOC_JSON,
			COVER_1_JSON));

		mockMvc.perform(post("/documents")
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
			.andExpect(jsonPath("$.data.parentId").value(parentId.toString()))
			.andExpect(jsonPath("$.data.title").value(PROJECT_OVERVIEW_TITLE))
			.andExpect(jsonPath("$.data.sortKey").value("00000000000000000003"))
			.andExpect(jsonPath("$.data.icon.type").value("emoji"))
			.andExpect(jsonPath("$.data.cover.value").value("cover-1"))
			.andExpect(jsonPath("$.data.createdBy").value(ACTOR_ID));
	}

	@Test
	@DisplayName("성공_내 문서 목록 조회 요청에 대해 문서 배열 응답을 반환한다")
	void getDocumentsReturnsEnvelope() throws Exception {
		UUID rootDocumentId = UUID.randomUUID();
		UUID childDocumentId = UUID.randomUUID();

		when(documentService.getAllByUserId(ACTOR_ID)).thenReturn(List.of(
				document(rootDocumentId, UUID.randomUUID(), null, ROOT_DOCUMENT_TITLE, ACTOR_ID, 0,
				"00000000000000000001", null, null),
				document(childDocumentId, UUID.randomUUID(), rootDocumentId, CHILD_DOCUMENT_TITLE, ACTOR_ID, 1,
				"00000000000000000002", null, null)
		));

		mockMvc.perform(get("/documents").header(USER_ID_HEADER, ACTOR_ID))
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
	@DisplayName("실패_인증 헤더가 없으면 문서 목록 조회에서 인증 오류를 반환한다")
	void getDocumentsRequiresUserHeader() throws Exception {
		var result = mockMvc.perform(get("/documents"));

		ApiResponseAssertions.assertErrorEnvelope(result, "UNAUTHORIZED", 9001, "인증 정보가 없습니다.");
	}

	@Test
	@DisplayName("실패_문서 제목이 공백이면 유효성 검사 오류를 반환한다")
	void createDocumentRejectsBlankTitle() throws Exception {
		var result = mockMvc.perform(post("/documents")
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
	@DisplayName("실패_부모 문서가 다른 사용자 소유 문서이면 잘못된 요청 응답을 반환한다")
	void createDocumentReturnsBadRequestWhenParentBelongsToOtherUser() throws Exception {
		when(documentService.create(
			eq(UUID.fromString("11111111-1111-1111-1111-111111111111")),
			eq(PROJECT_OVERVIEW_TITLE),
			eq(null),
			eq(null),
			eq(ACTOR_ID)
		)).thenThrow(new BusinessException(BusinessErrorCode.INVALID_REQUEST));

		var result = mockMvc.perform(post("/documents")
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
		UUID parentId = UUID.fromString("11111111-1111-1111-1111-111111111111");

		when(documentService.create(
			eq(parentId),
			eq(PROJECT_OVERVIEW_TITLE),
			eq(null),
			eq(null),
			eq(ACTOR_ID)
		)).thenThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));

		var result = mockMvc.perform(post("/documents")
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
		UUID documentId = UUID.randomUUID();

		when(documentService.getById(documentId))
				.thenReturn(document(documentId, UUID.randomUUID(), null, PROJECT_OVERVIEW_TITLE, ACTOR_ID, 2,
				"00000000000000000007",
				ICON_DOC_JSON,
				null));

		mockMvc.perform(get("/documents/{documentId}", documentId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data.id").value(documentId.toString()))
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

		var result = mockMvc.perform(get("/documents/{documentId}", documentId));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("성공_soft delete된 문서 복구 요청에 대해 성공 응답을 반환한다")
	void restoreDocumentReturnsSuccessEnvelope() throws Exception {
		UUID documentId = UUID.randomUUID();

		mockMvc.perform(post("/documents/{documentId}/restore", documentId)
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

		var result = mockMvc.perform(post("/documents/{documentId}/restore", documentId)
			.header(USER_ID_HEADER, ACTOR_ID));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_이미 활성 상태인 문서 복구 요청은 문서 없음 응답을 반환한다")
	void restoreDocumentReturnsNotFoundWhenDocumentAlreadyActive() throws Exception {
		UUID documentId = UUID.randomUUID();
		doThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND))
			.when(documentService).restore(documentId, ACTOR_ID);

		var result = mockMvc.perform(post("/documents/{documentId}/restore", documentId)
			.header(USER_ID_HEADER, ACTOR_ID));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_휴지통 보관 시간 5분이 지난 문서 복구 요청은 문서 없음 응답을 반환한다")
	void restoreDocumentReturnsNotFoundWhenTrashRetentionExpired() throws Exception {
		UUID documentId = UUID.randomUUID();
		doThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND))
			.when(documentService).restore(documentId, ACTOR_ID);

		var result = mockMvc.perform(post("/documents/{documentId}/restore", documentId)
			.header(USER_ID_HEADER, ACTOR_ID));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}


	@Test
	@DisplayName("실패_사용자 식별자 헤더 없이 문서 복구 요청하면 인증 오류 응답을 반환한다")
	void restoreDocumentReturnsUnauthorizedWhenHeaderMissing() throws Exception {
		UUID documentId = UUID.randomUUID();

		var result = mockMvc.perform(post("/documents/{documentId}/restore", documentId));

		ApiResponseAssertions.assertErrorEnvelope(result, "UNAUTHORIZED", 9001, "인증 정보가 없습니다.");
		verify(documentService, never()).restore(any(), any());
	}

	@Test
	@DisplayName("실패_icon이 객체 스키마를 따르지 않으면 유효성 검사 오류를 반환한다")
	void createDocumentRejectsInvalidIconSchema() throws Exception {
		var result = mockMvc.perform(post("/documents")
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
		var result = mockMvc.perform(post("/documents")
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
		var result = mockMvc.perform(post("/documents")
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
		UUID ownerId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();

		when(documentService.update(
			eq(documentId),
			eq(UPDATED_PROJECT_OVERVIEW_TITLE),
			eq(ICON_DOC_JSON),
			eq(COVER_2_JSON),
				eq(2),
			eq(ACTOR_ID)
			)).thenReturn(document(documentId, ownerId, null, UPDATED_PROJECT_OVERVIEW_TITLE, ACTOR_ID, 3,
			"00000000000000000007",
			ICON_DOC_JSON,
			COVER_2_JSON));

		mockMvc.perform(patch("/documents/{documentId}", documentId)
				.contentType("application/json")
				.header(USER_ID_HEADER, ACTOR_ID)
				.content("""
					{
					  "title": "수정된 프로젝트 개요",
					  "version": 2,
					  "icon": {
					    "type": "emoji",
					    "value": "📄"
					  },
					  "cover": {
					    "type": "image",
					    "value": "cover-2"
					  }
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.message").value("요청 응답 성공"))
			.andExpect(jsonPath("$.data.id").value(documentId.toString()))
			.andExpect(jsonPath("$.data.parentId").doesNotExist())
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
				eq(1),
			eq(ACTOR_ID)
		)).thenThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));

		var result = mockMvc.perform(patch("/documents/{documentId}", documentId)
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "title": "수정된 프로젝트 개요",
				  "version": 1
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("성공_문서 수정 요청에 변경 내용이 없어도 기존 version을 그대로 응답한다")
	void updateDocumentReturnsSameVersionWhenRequestIsNoOp() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();

		when(documentService.update(
			eq(documentId),
			eq(PROJECT_OVERVIEW_TITLE),
			eq(ICON_DOC_JSON),
			eq(COVER_1_JSON),
				eq(3),
			eq(ACTOR_ID)
			)).thenReturn(document(documentId, ownerId, null, PROJECT_OVERVIEW_TITLE, ACTOR_ID, 3,
			"00000000000000000007",
			ICON_DOC_JSON,
			COVER_1_JSON));

		mockMvc.perform(patch("/documents/{documentId}", documentId)
				.contentType("application/json")
				.header(USER_ID_HEADER, ACTOR_ID)
				.content("""
					{
					  "title": "프로젝트 개요",
					  "version": 3,
					  "icon": {
					    "type": "emoji",
					    "value": "📄"
					  },
					  "cover": {
					    "type": "image",
					    "value": "cover-1"
					  }
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.title").value(PROJECT_OVERVIEW_TITLE))
			.andExpect(jsonPath("$.data.version").value(3));
	}

	@Test
	@DisplayName("실패_문서 수정 요청에 제목이 없으면 유효성 검사 오류를 반환한다")
	void updateDocumentRejectsMissingTitle() throws Exception {
		UUID documentId = UUID.randomUUID();

		var result = mockMvc.perform(patch("/documents/{documentId}", documentId)
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "icon": null
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
		verifyNoInteractions(documentService);
	}

	@Test
	@DisplayName("실패_문서 수정 요청에 version이 없으면 유효성 검사 오류를 반환한다")
	void updateDocumentRejectsMissingVersion() throws Exception {
		UUID documentId = UUID.randomUUID();

		var result = mockMvc.perform(patch("/documents/{documentId}", documentId)
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "title": "수정된 프로젝트 개요"
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
		verifyNoInteractions(documentService);
	}

	@Test
	@DisplayName("실패_문서 수정 제목이 공백이면 유효성 검사 오류를 반환한다")
	void updateDocumentRejectsBlankTitle() throws Exception {
		UUID documentId = UUID.randomUUID();

		var result = mockMvc.perform(patch("/documents/{documentId}", documentId)
			.contentType("application/json")
			.header("X-User-Id", "user-123")
			.content("""
				{
				  "title": " ",
				  "version": 1
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
		verifyNoInteractions(documentService);
	}

	@Test
	@DisplayName("실패_문서 수정 요청의 version이 현재 문서와 다르면 충돌 응답을 반환한다")
	void updateDocumentReturnsConflictWhenVersionMismatch() throws Exception {
		UUID documentId = UUID.randomUUID();

		when(documentService.update(
			eq(documentId),
			eq(UPDATED_PROJECT_OVERVIEW_TITLE),
			eq(null),
			eq(null),
				eq(1),
			eq(ACTOR_ID)
		)).thenThrow(new BusinessException(BusinessErrorCode.CONFLICT));

		var result = mockMvc.perform(patch("/documents/{documentId}", documentId)
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "title": "수정된 프로젝트 개요",
				  "version": 1
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "CONFLICT", 9005, "요청이 현재 리소스 상태와 충돌합니다.");
	}

	@Test
	@DisplayName("실패_문서 수정 요청에 인증 헤더가 없으면 인증 오류를 반환한다")
	void updateDocumentRequiresUserHeader() throws Exception {
		UUID documentId = UUID.randomUUID();

		var result = mockMvc.perform(patch("/documents/{documentId}", documentId)
			.contentType("application/json")
			.content("""
				{
				  "title": "수정된 프로젝트 개요",
				  "version": 1
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "UNAUTHORIZED", 9001, "인증 정보가 없습니다.");
	}

	@Test
	@DisplayName("성공_PRIVATE 문서 공개 상태를 PUBLIC으로 수정하면 수정 응답을 반환한다")
	void updateDocumentVisibilityReturnsEnvelope() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
			Document document = document(documentId, ownerId, null, PROJECT_OVERVIEW_TITLE, ACTOR_ID, 4,
			"00000000000000000007",
			ICON_DOC_JSON,
			COVER_1_JSON);
		document.setVisibility(DocumentVisibility.PUBLIC);

		when(documentService.updateVisibility(
			eq(documentId),
			eq(DocumentVisibility.PUBLIC),
				eq(3),
			eq(ACTOR_ID)
		)).thenReturn(document);

		mockMvc.perform(patch("/documents/{documentId}/visibility", documentId)
				.contentType("application/json")
				.header(USER_ID_HEADER, ACTOR_ID)
				.content("""
					{
					  "visibility": "PUBLIC",
					  "version": 3
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(documentId.toString()))
			.andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
			.andExpect(jsonPath("$.data.version").value(4));
	}

	@Test
	@DisplayName("성공_PUBLIC 문서 공개 상태를 PRIVATE로 수정하면 수정 응답을 반환한다")
	void updateDocumentVisibilityReturnsEnvelopeWhenChangingPublicToPrivate() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
			Document document = document(documentId, ownerId, null, PROJECT_OVERVIEW_TITLE, ACTOR_ID, 6,
			"00000000000000000007",
			ICON_DOC_JSON,
			COVER_1_JSON);
		document.setVisibility(DocumentVisibility.PRIVATE);

		when(documentService.updateVisibility(
			eq(documentId),
			eq(DocumentVisibility.PRIVATE),
				eq(5),
			eq(ACTOR_ID)
		)).thenReturn(document);

		mockMvc.perform(patch("/documents/{documentId}/visibility", documentId)
				.contentType("application/json")
				.header(USER_ID_HEADER, ACTOR_ID)
				.content("""
					{
					  "visibility": "PRIVATE",
					  "version": 5
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(documentId.toString()))
			.andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
			.andExpect(jsonPath("$.data.version").value(6));
	}

	@Test
	@DisplayName("성공_동일 공개 상태 요청이면 기존 version을 유지한 응답을 반환한다")
	void updateDocumentVisibilityReturnsSameVersionWhenRequestedStateIsSame() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
			Document document = document(documentId, ownerId, null, PROJECT_OVERVIEW_TITLE, ACTOR_ID, 7,
			"00000000000000000007",
			ICON_DOC_JSON,
			COVER_1_JSON);
		document.setVisibility(DocumentVisibility.PRIVATE);

		when(documentService.updateVisibility(
			eq(documentId),
			eq(DocumentVisibility.PRIVATE),
				eq(7),
			eq(ACTOR_ID)
		)).thenReturn(document);

		mockMvc.perform(patch("/documents/{documentId}/visibility", documentId)
				.contentType("application/json")
				.header(USER_ID_HEADER, ACTOR_ID)
				.content("""
					{
					  "visibility": "PRIVATE",
					  "version": 7
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(documentId.toString()))
			.andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
			.andExpect(jsonPath("$.data.version").value(7));
	}

	@Test
	@DisplayName("실패_공개 상태 변경 요청 version이 현재 문서와 다르면 충돌 응답을 반환한다")
	void updateDocumentVisibilityReturnsConflictWhenVersionMismatch() throws Exception {
		UUID documentId = UUID.randomUUID();

		when(documentService.updateVisibility(
			eq(documentId),
			eq(DocumentVisibility.PRIVATE),
				eq(2),
			eq(ACTOR_ID)
		)).thenThrow(new BusinessException(BusinessErrorCode.CONFLICT));

		var result = mockMvc.perform(patch("/documents/{documentId}/visibility", documentId)
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "visibility": "PRIVATE",
				  "version": 2
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "CONFLICT", 9005, "요청이 현재 리소스 상태와 충돌합니다.");
	}

	@Test
	@DisplayName("실패_허용되지 않은 공개 상태값이면 유효성 검사 오류를 반환한다")
	void updateDocumentVisibilityRejectsInvalidVisibility() throws Exception {
		UUID documentId = UUID.randomUUID();

		var result = mockMvc.perform(patch("/documents/{documentId}/visibility", documentId)
			.contentType("application/json")
			.header(USER_ID_HEADER, ACTOR_ID)
			.content("""
				{
				  "visibility": "INTERNAL",
				  "version": 2
				}
				"""));

		ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
		verifyNoInteractions(documentService);
	}

	@Test
	@DisplayName("성공_정상 삭제 요청 시 성공 응답을 반환한다")
	void deleteDocumentReturnsSuccessEnvelope() throws Exception {
		UUID documentId = UUID.randomUUID();
		doNothing().when(documentService).delete(documentId, ACTOR_ID);

		mockMvc.perform(delete("/documents/{documentId}", documentId)
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

		var result = mockMvc.perform(delete("/documents/{documentId}", documentId)
			.header(USER_ID_HEADER, ACTOR_ID));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_이미 soft delete된 문서 삭제 시 문서 없음 응답을 반환한다")
	void deleteDocumentReturnsNotFoundWhenDocumentAlreadyDeleted() throws Exception {
		UUID documentId = UUID.randomUUID();
		doThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND))
			.when(documentService).delete(documentId, ACTOR_ID);

		var result = mockMvc.perform(delete("/documents/{documentId}", documentId)
			.header(USER_ID_HEADER, ACTOR_ID));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_X-User-Id 헤더가 없으면 인증 오류를 반환한다")
	void deleteDocumentReturnsUnauthorizedWhenHeaderMissing() throws Exception {
		UUID documentId = UUID.randomUUID();

		var result = mockMvc.perform(delete("/documents/{documentId}", documentId));

		ApiResponseAssertions.assertErrorEnvelope(result, "UNAUTHORIZED", 9001, "인증 정보가 없습니다.");
	}

	@Test
	@DisplayName("성공_PATCH 문서 휴지통 이동 요청 시 성공 응답을 반환한다")
	void trashDocumentReturnsSuccessEnvelope() throws Exception {
		UUID documentId = UUID.randomUUID();
		doNothing().when(documentService).trash(documentId, ACTOR_ID);

		mockMvc.perform(patch("/documents/{documentId}/trash", documentId)
				.header(USER_ID_HEADER, ACTOR_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.httpStatus").value("OK"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("요청 응답 성공"))
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data").doesNotExist());

		verify(documentService).trash(documentId, ACTOR_ID);
	}

	@Test
	@DisplayName("실패_존재하지 않는 문서 휴지통 이동 시 문서 없음 응답을 반환한다")
	void trashDocumentReturnsNotFoundWhenDocumentMissing() throws Exception {
		UUID documentId = UUID.randomUUID();
		doThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND))
			.when(documentService).trash(documentId, ACTOR_ID);

		var result = mockMvc.perform(patch("/documents/{documentId}/trash", documentId)
			.header(USER_ID_HEADER, ACTOR_ID));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_이미 휴지통 상태인 문서 휴지통 이동 시 문서 없음 응답을 반환한다")
	void trashDocumentReturnsNotFoundWhenDocumentAlreadyTrashed() throws Exception {
		UUID documentId = UUID.randomUUID();
		doThrow(new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND))
			.when(documentService).trash(documentId, ACTOR_ID);

		var result = mockMvc.perform(patch("/documents/{documentId}/trash", documentId)
			.header(USER_ID_HEADER, ACTOR_ID));

		ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("실패_X-User-Id 헤더가 없으면 문서 휴지통 이동은 인증 오류를 반환한다")
	void trashDocumentReturnsUnauthorizedWhenHeaderMissing() throws Exception {
		UUID documentId = UUID.randomUUID();

		var result = mockMvc.perform(patch("/documents/{documentId}/trash", documentId));

		ApiResponseAssertions.assertErrorEnvelope(result, "UNAUTHORIZED", 9001, "인증 정보가 없습니다.");
		verify(documentService, never()).trash(any(), any());
	}
}
