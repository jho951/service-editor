package com.documents.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.documents.domain.Document;
import com.documents.domain.DocumentVisibility;
import com.documents.domain.Workspace;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.DocumentRepository;
import com.documents.support.OrderedSortKeyGenerator;
import com.documents.support.TextNormalizer;

@ExtendWith(MockitoExtension.class)
@DisplayName("Document 서비스 구현 검증")
class DocumentServiceImplTest {

	private static final String ACTOR_ID = "user-123";
	private static final String ROOT_TITLE = "프로젝트 개요";

	@Mock
	private BlockService blockService;

	@Mock
	private DocumentRepository documentRepository;

	@Mock
	private WorkspaceService workspaceService;

	@Mock
	private TextNormalizer textNormalizer;

	@Mock
	private OrderedSortKeyGenerator orderedSortKeyGenerator;

	@InjectMocks
	private DocumentServiceImpl documentService;

	@Test
	@DisplayName("성공_루트 문서 생성 시 워크스페이스와 사용자 식별자를 정규화하여 저장한다")
	void createRootDocumentNormalizesActorAndStoresDocument() {
		UUID workspaceId = UUID.randomUUID();
		when(workspaceService.getById(workspaceId)).thenReturn(workspace(workspaceId));
		when(textNormalizer.normalizeNullable(" " + ACTOR_ID + " ")).thenReturn(ACTOR_ID);
		when(textNormalizer.normalizeRequired("  " + ROOT_TITLE + "  ")).thenReturn(ROOT_TITLE);
		when(documentRepository.findActiveByWorkspaceIdAndParentIdOrderBySortKey(workspaceId, null)).thenReturn(List.of());
		when(orderedSortKeyGenerator.generate(anyList(), any(), any(), isNull(), isNull()))
			.thenReturn("000000000001000000000000");
		when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Document result = documentService.create(
			workspaceId,
			null,
			"  " + ROOT_TITLE + "  ",
			"{\"type\":\"emoji\",\"value\":\"📄\"}",
			"{\"type\":\"image\",\"value\":\"cover-1\"}",
			" " + ACTOR_ID + " "
		);

		ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
		verify(documentRepository).save(captor.capture());
		Document request = captor.getValue();

		assertThat(request.getId()).isNotNull();
		assertThat(request.getWorkspaceId()).isEqualTo(workspaceId);
		assertThat(request.getParentId()).isNull();
		assertThat(request.getTitle()).isEqualTo(ROOT_TITLE);
		assertThat(request.getIconJson()).isEqualTo("{\"type\":\"emoji\",\"value\":\"📄\"}");
		assertThat(request.getCoverJson()).isEqualTo("{\"type\":\"image\",\"value\":\"cover-1\"}");
		assertThat(request.getSortKey()).isEqualTo("000000000001000000000000");
		assertThat(request.getCreatedBy()).isEqualTo(ACTOR_ID);
		assertThat(request.getUpdatedBy()).isEqualTo(ACTOR_ID);
		assertThat(result).isSameAs(request);
	}

	@Test
	@DisplayName("성공_사용자 식별자 헤더가 공백이면 감사 필드를 null로 저장한다")
	void createDocumentStoresNullActorWhenHeaderIsBlank() {
		UUID workspaceId = UUID.randomUUID();
		when(workspaceService.getById(workspaceId)).thenReturn(workspace(workspaceId));
		when(textNormalizer.normalizeNullable(" ")).thenReturn(null);
		when(textNormalizer.normalizeRequired(ROOT_TITLE)).thenReturn(ROOT_TITLE);
		when(documentRepository.findActiveByWorkspaceIdAndParentIdOrderBySortKey(workspaceId, null)).thenReturn(List.of());
		when(orderedSortKeyGenerator.generate(anyList(), any(), any(), isNull(), isNull()))
			.thenReturn("000000000001000000000000");
		when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Document result = documentService.create(workspaceId, null, ROOT_TITLE, null, null, " ");

		assertThat(result.getCreatedBy()).isNull();
		assertThat(result.getUpdatedBy()).isNull();
	}

	@Test
	@DisplayName("성공_부모 문서가 같은 워크스페이스에 있으면 하위 문서를 저장한다")
	void createChildDocumentWhenParentBelongsToSameWorkspace() {
		UUID workspaceId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();
		when(workspaceService.getById(workspaceId)).thenReturn(workspace(workspaceId));
		when(documentRepository.findByIdAndDeletedAtIsNull(parentId))
			.thenReturn(Optional.of(parentDocument(parentId, workspaceId)));
		when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);
		when(textNormalizer.normalizeRequired("하위 문서")).thenReturn("하위 문서");
		when(documentRepository.findActiveByWorkspaceIdAndParentIdOrderBySortKey(workspaceId, parentId)).thenReturn(List.of());
		when(orderedSortKeyGenerator.generate(anyList(), any(), any(), isNull(), isNull()))
			.thenReturn("000000000001000000000000");
		when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Document result = documentService.create(workspaceId, parentId, "하위 문서", null, null, ACTOR_ID);

		assertThat(result.getParentId()).isEqualTo(parentId);
		assertThat(result.getSortKey()).isEqualTo("000000000001000000000000");
		verify(documentRepository).findByIdAndDeletedAtIsNull(parentId);
	}

	@Test
	@DisplayName("실패_워크스페이스가 없으면 문서 생성 시 워크스페이스 없음 예외를 던진다")
	void createDocumentThrowsWhenWorkspaceMissing() {
		UUID workspaceId = UUID.randomUUID();
		when(workspaceService.getById(workspaceId))
			.thenThrow(new BusinessException(BusinessErrorCode.WORKSPACE_NOT_FOUND));

		assertThatThrownBy(() -> documentService.create(workspaceId, null, ROOT_TITLE, null, null, ACTOR_ID))
			.isInstanceOf(BusinessException.class)
			.hasMessage("요청한 워크스페이스를 찾을 수 없습니다.")
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.WORKSPACE_NOT_FOUND);
	}

	@Test
	@DisplayName("실패_부모 문서가 없으면 문서 생성 시 문서 없음 예외를 던진다")
	void createDocumentThrowsWhenParentMissing() {
		UUID workspaceId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();
		when(workspaceService.getById(workspaceId)).thenReturn(workspace(workspaceId));
		when(documentRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> documentService.create(workspaceId, parentId, ROOT_TITLE, null, null, ACTOR_ID))
			.isInstanceOf(BusinessException.class)
			.hasMessage("요청한 문서를 찾을 수 없습니다.")
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.DOCUMENT_NOT_FOUND);
	}

	@Test
	@DisplayName("실패_부모 문서가 다른 워크스페이스에 있으면 잘못된 요청 예외를 던진다")
	void createDocumentThrowsWhenParentBelongsToOtherWorkspace() {
		UUID workspaceId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();
		when(workspaceService.getById(workspaceId)).thenReturn(workspace(workspaceId));
		when(documentRepository.findByIdAndDeletedAtIsNull(parentId))
			.thenReturn(Optional.of(parentDocument(parentId, UUID.randomUUID())));

		assertThatThrownBy(() -> documentService.create(workspaceId, parentId, ROOT_TITLE, null, null, ACTOR_ID))
			.isInstanceOf(BusinessException.class)
			.hasMessage("잘못된 요청입니다.")
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.INVALID_REQUEST);
	}

	@Test
	@DisplayName("성공_워크스페이스 문서 목록 조회 시 활성 문서를 반환한다")
	void getDocumentsByWorkspaceIdReturnsActive() {
		UUID workspaceId = UUID.randomUUID();
		Document rootDocument = document(workspaceId, null, "루트 문서", "00000000000000000001");
		Document childDocument = document(workspaceId, rootDocument.getId(), "하위 문서", "00000000000000000002");
		when(workspaceService.getById(workspaceId)).thenReturn(workspace(workspaceId));
		when(documentRepository.findActiveByWorkspaceIdOrderBySortKey(eq(workspaceId)))
			.thenReturn(List.of(rootDocument, childDocument));

		assertThat(documentService.getAllByWorkspaceId(workspaceId))
			.containsExactly(rootDocument, childDocument);
	}

	@Test
	@DisplayName("실패_존재하지 않는 워크스페이스의 문서 목록 조회는 워크스페이스 없음 예외를 던진다")
	void getAllByWorkspaceIdThrowsWhenWorkspaceMissing() {
		UUID workspaceId = UUID.randomUUID();
		when(workspaceService.getById(workspaceId))
			.thenThrow(new BusinessException(BusinessErrorCode.WORKSPACE_NOT_FOUND));

		assertThatThrownBy(() -> documentService.getAllByWorkspaceId(workspaceId))
			.isInstanceOf(BusinessException.class)
			.hasMessage("요청한 워크스페이스를 찾을 수 없습니다.")
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.WORKSPACE_NOT_FOUND);
	}

	@Test
	@DisplayName("성공_문서 단건 조회 시 활성 문서를 반환한다")
	void getByIdReturnsActiveDocument() {
		UUID documentId = UUID.randomUUID();
		Document document = document(documentId, UUID.randomUUID(), null, "프로젝트 개요", "00000000000000000001");
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));

		assertThat(documentService.getById(documentId)).isSameAs(document);
	}

	@Test
	@DisplayName("실패_soft delete된 문서는 단건 조회에서 제외한다")
	void getByIdThrowsWhenDocumentMissing() {
		UUID documentId = UUID.randomUUID();
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> documentService.getById(documentId))
			.isInstanceOf(BusinessException.class)
			.hasMessage("요청한 문서를 찾을 수 없습니다.")
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.DOCUMENT_NOT_FOUND);
	}

	@Test
	@DisplayName("성공_문서 수정 시 제목은 trim 후 저장하고 updatedBy를 갱신한다")
	void updateDocumentTrimsTitleAndUpdatesAuditFields() {
		UUID workspaceId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		Document document = document(documentId, workspaceId, null, "기존 제목", "00000000000000000001");
		document.setVersion(3);
		document.setIconJson("{\"type\":\"emoji\",\"value\":\"😀\"}");
		document.setCoverJson("{\"type\":\"image\",\"value\":\"cover-1\"}");
		document.setUpdatedBy("old-user");
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(textNormalizer.normalizeRequired("  수정된 제목  ")).thenReturn("수정된 제목");
		when(textNormalizer.normalizeNullable((String)null)).thenReturn(null);
		when(textNormalizer.normalizeNullable(" user-456 ")).thenReturn("user-456");

		Document result = documentService.update(
			documentId,
			"  수정된 제목  ",
			null,
			null,
			3,
			" user-456 "
		);

		assertThat(result).isSameAs(document);
		assertThat(result.getTitle()).isEqualTo("수정된 제목");
		assertThat(result.getParentId()).isNull();
		assertThat(result.getIconJson()).isNull();
		assertThat(result.getCoverJson()).isNull();
		assertThat(result.getUpdatedBy()).isEqualTo("user-456");
	}

	@Test
	@DisplayName("성공_title만 수정하면 다른 필드는 유지한다")
	void updateDocumentUpdatesOnlyTitle() {
		UUID workspaceId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		Document document = document(documentId, workspaceId, null, "기존 제목", "00000000000000000001");
		document.setVersion(4);
		document.setIconJson("{\"type\":\"emoji\",\"value\":\"😀\"}");
		document.setCoverJson("{\"type\":\"image\",\"value\":\"cover-1\"}");
		document.setUpdatedBy("old-user");
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(textNormalizer.normalizeRequired("  새 제목  ")).thenReturn("새 제목");
		when(textNormalizer.normalizeNullable("{\"type\":\"emoji\",\"value\":\"😀\"}"))
			.thenReturn("{\"type\":\"emoji\",\"value\":\"😀\"}");
		when(textNormalizer.normalizeNullable("{\"type\":\"image\",\"value\":\"cover-1\"}"))
			.thenReturn("{\"type\":\"image\",\"value\":\"cover-1\"}");
		when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);

		Document result = documentService.update(
			documentId,
			"  새 제목  ",
			"{\"type\":\"emoji\",\"value\":\"😀\"}",
			"{\"type\":\"image\",\"value\":\"cover-1\"}",
			4,
			ACTOR_ID
		);

		assertThat(result.getTitle()).isEqualTo("새 제목");
		assertThat(result.getParentId()).isNull();
		assertThat(result.getIconJson()).isEqualTo("{\"type\":\"emoji\",\"value\":\"😀\"}");
		assertThat(result.getCoverJson()).isEqualTo("{\"type\":\"image\",\"value\":\"cover-1\"}");
		assertThat(result.getUpdatedBy()).isEqualTo(ACTOR_ID);
	}

	@Test
	@DisplayName("성공_title이 null이면 기존 제목을 유지하고 메타데이터만 갱신한다")
	void updateDocumentKeepsTitleWhenNullAndUpdatesMetadataOnly() {
		UUID workspaceId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();
		Document document = document(documentId, workspaceId, parentId, "기존 제목", "00000000000000000001");
		document.setVersion(5);
		document.setIconJson("{\"type\":\"emoji\",\"value\":\"😀\"}");
		document.setCoverJson("{\"type\":\"image\",\"value\":\"cover-1\"}");
		document.setUpdatedBy("old-user");
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(textNormalizer.normalizeNullable((String)null)).thenReturn(null);
		when(textNormalizer.normalizeNullable("{\"type\":\"emoji\",\"value\":\"📄\"}"))
			.thenReturn("{\"type\":\"emoji\",\"value\":\"📄\"}");
		when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);

		Document result = documentService.update(
			documentId,
			null,
			"{\"type\":\"emoji\",\"value\":\"📄\"}",
			null,
			5,
			ACTOR_ID
		);

		assertThat(result.getTitle()).isEqualTo("기존 제목");
		assertThat(result.getParentId()).isEqualTo(parentId);
		assertThat(result.getIconJson()).isEqualTo("{\"type\":\"emoji\",\"value\":\"📄\"}");
		assertThat(result.getCoverJson()).isNull();
		assertThat(result.getUpdatedBy()).isEqualTo(ACTOR_ID);
	}

	@Test
	@DisplayName("성공_actorId가 공백이면 updatedBy를 null로 저장한다")
	void updateDocumentStoresNullUpdatedByWhenActorBlank() {
		UUID documentId = UUID.randomUUID();
		Document document = document(documentId, UUID.randomUUID(), null, "기존 제목", "00000000000000000001");
		document.setVersion(6);
		document.setIconJson("{\"type\":\"emoji\",\"value\":\"😀\"}");
		document.setCoverJson("{\"type\":\"image\",\"value\":\"cover-1\"}");
		document.setUpdatedBy("old-user");
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(textNormalizer.normalizeRequired("수정된 제목")).thenReturn("수정된 제목");
		when(textNormalizer.normalizeNullable("{\"type\":\"emoji\",\"value\":\"😀\"}"))
			.thenReturn("{\"type\":\"emoji\",\"value\":\"😀\"}");
		when(textNormalizer.normalizeNullable("{\"type\":\"image\",\"value\":\"cover-1\"}"))
			.thenReturn("{\"type\":\"image\",\"value\":\"cover-1\"}");
		when(textNormalizer.normalizeNullable(" ")).thenReturn(null);

		Document result = documentService.update(
			documentId,
			"수정된 제목",
			"{\"type\":\"emoji\",\"value\":\"😀\"}",
			"{\"type\":\"image\",\"value\":\"cover-1\"}",
			6,
			" "
		);

		assertThat(result.getUpdatedBy()).isNull();
		assertThat(result.getTitle()).isEqualTo("수정된 제목");
	}

	@Test
	@DisplayName("성공_변경 내용이 모두 같으면 no-op으로 처리하고 updatedBy를 바꾸지 않는다")
	void updateDocumentDoesNothingWhenRequestedStateIsSame() {
		UUID workspaceId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		Document document = document(documentId, workspaceId, null, "기존 제목", "00000000000000000001");
		document.setVersion(7);
		document.setIconJson("{\"type\":\"emoji\",\"value\":\"😀\"}");
		document.setCoverJson("{\"type\":\"image\",\"value\":\"cover-1\"}");
		document.setUpdatedBy("old-user");
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(textNormalizer.normalizeRequired("기존 제목")).thenReturn("기존 제목");
		when(textNormalizer.normalizeNullable("{\"type\":\"emoji\",\"value\":\"😀\"}"))
			.thenReturn("{\"type\":\"emoji\",\"value\":\"😀\"}");
		when(textNormalizer.normalizeNullable("{\"type\":\"image\",\"value\":\"cover-1\"}"))
			.thenReturn("{\"type\":\"image\",\"value\":\"cover-1\"}");

		Document result = documentService.update(
			documentId,
			"기존 제목",
			"{\"type\":\"emoji\",\"value\":\"😀\"}",
			"{\"type\":\"image\",\"value\":\"cover-1\"}",
			7,
			ACTOR_ID
		);

		assertThat(result).isSameAs(document);
		assertThat(result.getTitle()).isEqualTo("기존 제목");
		assertThat(result.getIconJson()).isEqualTo("{\"type\":\"emoji\",\"value\":\"😀\"}");
		assertThat(result.getCoverJson()).isEqualTo("{\"type\":\"image\",\"value\":\"cover-1\"}");
		assertThat(result.getUpdatedBy()).isEqualTo("old-user");
		verify(textNormalizer, never()).normalizeNullable(ACTOR_ID);
	}

	@Test
	@DisplayName("실패_요청 version이 현재 문서 version과 다르면 충돌 예외를 던진다")
	void updateDocumentThrowsWhenVersionMismatch() {
		UUID documentId = UUID.randomUUID();
		Document document = document(documentId, UUID.randomUUID(), null, "기존 제목", "00000000000000000001");
		document.setVersion(3);
		document.setUpdatedBy("old-user");
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));

		assertThatThrownBy(() -> documentService.update(documentId, "새 제목", null, null, 2, ACTOR_ID))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.CONFLICT);

		assertThat(document.getTitle()).isEqualTo("기존 제목");
		assertThat(document.getUpdatedBy()).isEqualTo("old-user");
		verify(textNormalizer, never()).normalizeRequired(anyString());
		verify(textNormalizer, never()).normalizeNullable(ACTOR_ID);
	}

	@Test
	@DisplayName("성공_PUBLIC 문서를 PRIVATE로 변경하면 version 증가 대상 상태로 갱신한다")
	void updateVisibilityChangesPublicToPrivate() {
		UUID documentId = UUID.randomUUID();
		Document document = document(documentId, UUID.randomUUID(), null, "문서", "00000000000000000001");
		document.setVersion(3);
		document.setVisibility(DocumentVisibility.PUBLIC);
		document.setUpdatedBy("old-user");
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(textNormalizer.normalizeNullable(" user-456 ")).thenReturn("user-456");

		Document result = documentService.updateVisibility(documentId, DocumentVisibility.PRIVATE, 3, " user-456 ");

		assertThat(result).isSameAs(document);
		assertThat(result.getVisibility()).isEqualTo(DocumentVisibility.PRIVATE);
		assertThat(result.getUpdatedBy()).isEqualTo("user-456");
	}

	@Test
	@DisplayName("성공_PRIVATE 문서를 PUBLIC으로 변경하면 version 증가 대상 상태로 갱신한다")
	void updateVisibilityChangesPrivateToPublic() {
		UUID documentId = UUID.randomUUID();
		Document document = document(documentId, UUID.randomUUID(), null, "문서", "00000000000000000001");
		document.setVersion(4);
		document.setVisibility(DocumentVisibility.PRIVATE);
		document.setUpdatedBy("old-user");
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);

		Document result = documentService.updateVisibility(documentId, DocumentVisibility.PUBLIC, 4, ACTOR_ID);

		assertThat(result).isSameAs(document);
		assertThat(result.getVisibility()).isEqualTo(DocumentVisibility.PUBLIC);
		assertThat(result.getUpdatedBy()).isEqualTo(ACTOR_ID);
	}

	@Test
	@DisplayName("성공_동일 공개 상태 요청이면 no-op으로 처리하고 updatedBy를 바꾸지 않는다")
	void updateVisibilityDoesNothingWhenRequestedStateIsSame() {
		UUID documentId = UUID.randomUUID();
		Document document = document(documentId, UUID.randomUUID(), null, "문서", "00000000000000000001");
		document.setVersion(5);
		document.setVisibility(DocumentVisibility.PRIVATE);
		document.setUpdatedBy("old-user");
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));

		Document result = documentService.updateVisibility(documentId, DocumentVisibility.PRIVATE, 5, ACTOR_ID);

		assertThat(result).isSameAs(document);
		assertThat(result.getVisibility()).isEqualTo(DocumentVisibility.PRIVATE);
		assertThat(result.getUpdatedBy()).isEqualTo("old-user");
		verify(textNormalizer, never()).normalizeNullable(ACTOR_ID);
	}

	@Test
	@DisplayName("실패_공개 상태 변경 요청 version이 현재 문서와 다르면 충돌 예외를 던진다")
	void updateVisibilityThrowsWhenVersionMismatch() {
		UUID documentId = UUID.randomUUID();
		Document document = document(documentId, UUID.randomUUID(), null, "문서", "00000000000000000001");
		document.setVersion(6);
		document.setVisibility(DocumentVisibility.PRIVATE);
		document.setUpdatedBy("old-user");
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));

		assertThatThrownBy(() -> documentService.updateVisibility(documentId, DocumentVisibility.PUBLIC, 5, ACTOR_ID))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.CONFLICT);

		assertThat(document.getVisibility()).isEqualTo(DocumentVisibility.PRIVATE);
		assertThat(document.getUpdatedBy()).isEqualTo("old-user");
		verify(textNormalizer, never()).normalizeNullable(ACTOR_ID);
	}

	@Test
	@DisplayName("성공_문서 삭제 시 대상 문서를 즉시 hard delete 처리한다")
	void deleteHardDeletesDocument() {
		UUID documentId = UUID.randomUUID();
		Document targetDocument = document(documentId, UUID.randomUUID(), null, "삭제 대상 문서", "00000000000000000001");
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(targetDocument));

		documentService.delete(documentId, " user-456 ");

		verify(documentRepository).delete(targetDocument);
		verifyNoInteractions(blockService);
		verify(textNormalizer, never()).normalizeNullable(any());
	}

	@Test
	@DisplayName("성공_워크스페이스 휴지통 목록 조회는 deletedAt 내림차순으로 반환한다")
	void getTrashByWorkspaceIdReturnsDeletedDocumentsOrderedByDeletedAtDesc() {
		UUID workspaceId = UUID.randomUUID();
		Document olderDeletedDocument = deletedDocument(
			UUID.randomUUID(),
			workspaceId,
			null,
			"이전 삭제 문서",
			"00000000000000000001",
			LocalDateTime.now().minusMinutes(4)
		);
		Document newerDeletedDocument = deletedDocument(
			UUID.randomUUID(),
			workspaceId,
			null,
			"최근 삭제 문서",
			"00000000000000000002",
			LocalDateTime.now().minusMinutes(1)
		);
		when(workspaceService.getById(workspaceId)).thenReturn(workspace(workspaceId));
		when(documentRepository.findDeletedByWorkspaceIdOrderByDeletedAtDesc(workspaceId))
			.thenReturn(List.of(newerDeletedDocument, olderDeletedDocument));

		List<Document> result = documentService.getTrashByWorkspaceId(workspaceId);

		assertThat(result).containsExactly(newerDeletedDocument, olderDeletedDocument);
	}

	@Test
	@DisplayName("성공_문서 삭제는 하위 문서와 블록 정리를 위해 문서 엔티티 hard delete 경로를 사용한다")
	void deleteUsesDocumentHardDeletePath() {
		UUID rootId = UUID.randomUUID();
		Document rootDocument = document(rootId, UUID.randomUUID(), null, "루트 문서", "00000000000000000001");
		when(documentRepository.findByIdAndDeletedAtIsNull(rootId)).thenReturn(Optional.of(rootDocument));

		documentService.delete(rootId, ACTOR_ID);

		verify(documentRepository).delete(rootDocument);
		verify(documentRepository, never()).softDeleteActiveByIds(any(), any(), any());
		verifyNoInteractions(blockService);
	}

	@Test
	@DisplayName("실패_이미 삭제되었거나 없는 문서는 문서 없음 예외를 던진다")
	void deleteThrowsWhenDocumentMissing() {
		UUID documentId = UUID.randomUUID();

		assertThatThrownBy(() -> documentService.delete(documentId, ACTOR_ID))
			.isInstanceOf(BusinessException.class)
			.hasMessage("요청한 문서를 찾을 수 없습니다.")
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.DOCUMENT_NOT_FOUND);
	}

	@Test
	@DisplayName("성공_문서 휴지통 이동 시 문서와 활성 블록을 같은 시각으로 soft delete 처리한다")
	void trashSoftDeletesDocumentAndActiveBlocks() {
		UUID documentId = UUID.randomUUID();
		Document targetDocument = document(documentId, UUID.randomUUID(), null, "삭제 대상 문서", "00000000000000000001");
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(targetDocument));
		when(documentRepository.findActiveChildrenByParentIdOrderBySortKey(documentId)).thenReturn(List.of());
		when(textNormalizer.normalizeNullable(" user-456 ")).thenReturn("user-456");

		documentService.trash(documentId, " user-456 ");

		ArgumentCaptor<LocalDateTime> deletedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(documentRepository).softDeleteActiveByIds(eq(List.of(documentId)), eq("user-456"),
			deletedAtCaptor.capture());
		verify(blockService).softDeleteAllByDocumentId(eq(documentId), eq("user-456"), eq(deletedAtCaptor.getValue()));
	}

	@Test
	@DisplayName("성공_사용자 식별자가 공백이면 휴지통 이동도 null 사용자로 위임한다")
	void trashDelegatesNullActorToBlockService() {
		UUID documentId = UUID.randomUUID();
		Document targetDocument = document(documentId, UUID.randomUUID(), null, "삭제 대상 문서", "00000000000000000001");
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(targetDocument));
		when(documentRepository.findActiveChildrenByParentIdOrderBySortKey(documentId)).thenReturn(List.of());
		when(textNormalizer.normalizeNullable(" ")).thenReturn(null);

		documentService.trash(documentId, " ");

		ArgumentCaptor<LocalDateTime> deletedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(documentRepository).softDeleteActiveByIds(eq(List.of(documentId)), isNull(),
			deletedAtCaptor.capture());
		verify(blockService).softDeleteAllByDocumentId(eq(documentId), isNull(), eq(deletedAtCaptor.getValue()));
	}

	@Test
	@DisplayName("성공_문서 휴지통 이동 시 활성 하위 문서와 각 문서의 블록도 함께 soft delete 처리한다")
	void trashSoftDeletesDescendantDocumentsAndBlocks() {
		UUID workspaceId = UUID.randomUUID();
		UUID rootId = UUID.randomUUID();
		UUID childId = UUID.randomUUID();
		UUID grandChildId = UUID.randomUUID();
		Document rootDocument = document(rootId, workspaceId, null, "루트 문서", "00000000000000000001");
		Document childDocument = document(childId, workspaceId, rootId, "하위 문서", "00000000000000000002");
		Document grandChildDocument = document(grandChildId, workspaceId, childId, "손자 문서", "00000000000000000003");
		when(documentRepository.findByIdAndDeletedAtIsNull(rootId)).thenReturn(Optional.of(rootDocument));
		when(documentRepository.findActiveChildrenByParentIdOrderBySortKey(rootId)).thenReturn(List.of(childDocument));
		when(documentRepository.findActiveChildrenByParentIdOrderBySortKey(childId)).thenReturn(List.of(grandChildDocument));
		when(documentRepository.findActiveChildrenByParentIdOrderBySortKey(grandChildId)).thenReturn(List.of());
		when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);

		documentService.trash(rootId, ACTOR_ID);

		ArgumentCaptor<LocalDateTime> deletedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(documentRepository).softDeleteActiveByIds(eq(List.of(rootId, childId, grandChildId)),
			eq(ACTOR_ID), deletedAtCaptor.capture());
		verify(blockService).softDeleteAllByDocumentId(eq(rootId), eq(ACTOR_ID), eq(deletedAtCaptor.getValue()));
		verify(blockService).softDeleteAllByDocumentId(eq(childId), eq(ACTOR_ID), eq(deletedAtCaptor.getValue()));
		verify(blockService).softDeleteAllByDocumentId(eq(grandChildId), eq(ACTOR_ID), eq(deletedAtCaptor.getValue()));
	}

	@Test
	@DisplayName("실패_이미 휴지통 상태이거나 없는 문서를 휴지통 이동하면 문서 없음 예외를 던진다")
	void trashThrowsWhenDocumentMissing() {
		UUID documentId = UUID.randomUUID();

		assertThatThrownBy(() -> documentService.trash(documentId, ACTOR_ID))
			.isInstanceOf(BusinessException.class)
			.hasMessage("요청한 문서를 찾을 수 없습니다.")
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.DOCUMENT_NOT_FOUND);
	}

	@Test
	@DisplayName("성공_루트 삭제 문서는 부모 검증 없이 복구한다")
	void restoreRootDeletedDocument() {
		UUID documentId = UUID.randomUUID();
		Document deletedDocument = deletedDocument(documentId, UUID.randomUUID(), null, "삭제 문서",
			"00000000000000000001");
		when(documentRepository.findById(documentId)).thenReturn(Optional.of(deletedDocument));
		when(documentRepository.findDeletedChildrenByParentIdOrderBySortKey(documentId)).thenReturn(
			List.of());
		when(textNormalizer.normalizeNullable(" user-456 ")).thenReturn("user-456");

		documentService.restore(documentId, " user-456 ");

		ArgumentCaptor<LocalDateTime> restoredAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(documentRepository).restoreDeletedByIds(eq(List.of(documentId)), eq("user-456"),
			restoredAtCaptor.capture());
	}

	@Test
	@DisplayName("실패_휴지통 보관 시간 5분이 지난 문서는 복구할 수 없다")
	void restoreFailsWhenTrashRetentionExpired() {
		UUID documentId = UUID.randomUUID();
		Document expiredDeletedDocument = deletedDocument(
			documentId,
			UUID.randomUUID(),
			null,
			"만료된 삭제 문서",
			"00000000000000000001",
			LocalDateTime.now().minusMinutes(6)
		);
		when(documentRepository.findById(documentId)).thenReturn(Optional.of(expiredDeletedDocument));

		assertThatThrownBy(() -> documentService.restore(documentId, ACTOR_ID))
			.isInstanceOf(BusinessException.class)
			.hasMessage("요청한 문서를 찾을 수 없습니다.")
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.DOCUMENT_NOT_FOUND);

		verify(documentRepository, never()).restoreDeletedByIds(anyList(), any(), any());
		verify(blockService, never()).restoreAllByDocumentId(any(), any(), any());
	}

	@Test
	@DisplayName("성공_휴지통 보관 시간이 지난 루트 문서는 자동 영구 삭제 대상이 된다")
	void purgeExpiredTrashDeletesExpiredTrashRoot() {
		UUID documentId = UUID.randomUUID();
		Document expiredRoot = deletedDocument(
			documentId,
			UUID.randomUUID(),
			null,
			"만료된 루트 문서",
			"00000000000000000001",
			LocalDateTime.now().minusMinutes(6)
		);
		when(documentRepository.findExpiredTrashRoots(any(LocalDateTime.class))).thenReturn(List.of(expiredRoot));

		documentService.purgeExpiredTrash();

		verify(documentRepository).delete(expiredRoot);
	}

	@Test
	@DisplayName("성공_아직 5분이 지나지 않은 휴지통 문서는 자동 영구 삭제 대상이 아니다")
	void purgeExpiredTrashSkipsUnexpiredTrash() {
		when(documentRepository.findExpiredTrashRoots(any(LocalDateTime.class))).thenReturn(List.of());

		documentService.purgeExpiredTrash();

		verify(documentRepository, never()).delete(any(Document.class));
	}

	@Test
	@DisplayName("성공_자동 영구 삭제 대상이 없으면 안전하게 종료한다")
	void purgeExpiredTrashDoesNothingWhenNoExpiredTrashExists() {
		when(documentRepository.findExpiredTrashRoots(any(LocalDateTime.class))).thenReturn(List.of());

		documentService.purgeExpiredTrash();

		verify(documentRepository).findExpiredTrashRoots(any(LocalDateTime.class));
		verify(documentRepository, never()).delete(any(Document.class));
	}

	@Test
	@DisplayName("성공_활성 부모 밑 삭제 자식 문서는 복구한다")
	void restoreDeletedChildDocumentWhenParentIsActive() {
		UUID workspaceId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();
		UUID childId = UUID.randomUUID();
		Document deletedChild = deletedDocument(childId, workspaceId, parentId, "삭제 자식", "00000000000000000002");
		Document activeParent = document(parentId, workspaceId, null, "활성 부모", "00000000000000000001");
		when(documentRepository.findById(childId)).thenReturn(Optional.of(deletedChild));
		when(documentRepository.findById(parentId)).thenReturn(Optional.of(activeParent));
		when(documentRepository.findDeletedChildrenByParentIdOrderBySortKey(childId)).thenReturn(List.of());
		when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);

		documentService.restore(childId, ACTOR_ID);

		verify(documentRepository).restoreDeletedByIds(eq(List.of(childId)), eq(ACTOR_ID),
			any(LocalDateTime.class));
	}


	@Test
	@DisplayName("실패_삭제된 부모 밑 삭제 자식 문서는 단독 복구할 수 없다")
	void restoreFailsWhenParentIsDeleted() {
		UUID workspaceId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();
		UUID childId = UUID.randomUUID();
		Document deletedChild = deletedDocument(childId, workspaceId, parentId, "삭제 자식", "00000000000000000002");
		Document deletedParent = deletedDocument(parentId, workspaceId, null, "삭제 부모", "00000000000000000001");
		when(documentRepository.findById(childId)).thenReturn(Optional.of(deletedChild));
		when(documentRepository.findById(parentId)).thenReturn(Optional.of(deletedParent));

		assertThatThrownBy(() -> documentService.restore(childId, ACTOR_ID))
			.isInstanceOf(BusinessException.class)
			.hasMessage("잘못된 요청입니다.")
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.INVALID_REQUEST);

		verify(documentRepository, never()).restoreDeletedByIds(anyList(), any(), any());
	}

	@Test
	@DisplayName("실패_삭제된 부모 밑 삭제 자식 문서 복구 실패 시 블록 복구도 호출하지 않는다")
	void restoreDoesNotDelegateBlockRestoreWhenParentIsDeleted() {
		UUID workspaceId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();
		UUID childId = UUID.randomUUID();
		Document deletedChild = deletedDocument(childId, workspaceId, parentId, "삭제 자식", "00000000000000000002");
		Document deletedParent = deletedDocument(parentId, workspaceId, null, "삭제 부모", "00000000000000000001");
		when(documentRepository.findById(childId)).thenReturn(Optional.of(deletedChild));
		when(documentRepository.findById(parentId)).thenReturn(Optional.of(deletedParent));

		assertThatThrownBy(() -> documentService.restore(childId, ACTOR_ID))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.INVALID_REQUEST);

		verify(blockService, never()).restoreAllByDocumentId(any(), any(), any());
	}

	@Test
	@DisplayName("실패_활성 문서 복구 요청은 문서 없음 예외를 던진다")
	void restoreThrowsWhenDocumentIsAlreadyActive() {
		UUID documentId = UUID.randomUUID();
		when(documentRepository.findById(documentId))
			.thenReturn(Optional.of(document(documentId, UUID.randomUUID(), null, "활성 문서", "00000000000000000001")));

		assertThatThrownBy(() -> documentService.restore(documentId, ACTOR_ID))
			.isInstanceOf(BusinessException.class)
			.hasMessage("요청한 문서를 찾을 수 없습니다.")
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.DOCUMENT_NOT_FOUND);
	}

	@Test
	@DisplayName("성공_하위 문서까지 함께 복구될 때 각 문서의 블록 복구도 함께 호출한다")
	void restoreDelegatesBlockRestoreForDescendantDocuments() {
		UUID workspaceId = UUID.randomUUID();
		UUID rootId = UUID.randomUUID();
		UUID childId = UUID.randomUUID();
		Document deletedRoot = deletedDocument(rootId, workspaceId, null, "삭제 루트", "00000000000000000001");
		Document deletedChild = deletedDocument(childId, workspaceId, rootId, "삭제 자식", "00000000000000000002");
		when(documentRepository.findById(rootId)).thenReturn(Optional.of(deletedRoot));
		when(documentRepository.findDeletedChildrenByParentIdOrderBySortKey(rootId)).thenReturn(
			List.of(deletedChild));
		when(documentRepository.findDeletedChildrenByParentIdOrderBySortKey(childId)).thenReturn(List.of());
		when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);

		documentService.restore(rootId, ACTOR_ID);

		ArgumentCaptor<LocalDateTime> restoredAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(documentRepository).restoreDeletedByIds(eq(List.of(rootId, childId)), eq(ACTOR_ID),
			restoredAtCaptor.capture());
		verify(blockService).restoreAllByDocumentId(eq(rootId), eq(ACTOR_ID), eq(restoredAtCaptor.getValue()));
		verify(blockService).restoreAllByDocumentId(eq(childId), eq(ACTOR_ID), eq(restoredAtCaptor.getValue()));
	}

	@Test
	@DisplayName("성공_복구 대상이 아닌 다른 문서 블록은 복구 대상에 포함하지 않는다")
	void restoreDoesNotDelegateBlockRestoreForOtherDocument() {
		UUID workspaceId = UUID.randomUUID();
		UUID rootId = UUID.randomUUID();
		UUID childId = UUID.randomUUID();
		UUID otherDocumentId = UUID.randomUUID();
		Document deletedRoot = deletedDocument(rootId, workspaceId, null, "삭제 루트", "00000000000000000001");
		Document deletedChild = deletedDocument(childId, workspaceId, rootId, "삭제 자식", "00000000000000000002");
		when(documentRepository.findById(rootId)).thenReturn(Optional.of(deletedRoot));
		when(documentRepository.findDeletedChildrenByParentIdOrderBySortKey(rootId)).thenReturn(
			List.of(deletedChild));
		when(documentRepository.findDeletedChildrenByParentIdOrderBySortKey(childId)).thenReturn(List.of());
		when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);

		documentService.restore(rootId, ACTOR_ID);

		verify(blockService).restoreAllByDocumentId(eq(rootId), eq(ACTOR_ID), any(LocalDateTime.class));
		verify(blockService).restoreAllByDocumentId(eq(childId), eq(ACTOR_ID), any(LocalDateTime.class));
		verify(blockService, never()).restoreAllByDocumentId(eq(otherDocumentId), any(), any());
	}

	@Test
	@DisplayName("성공_targetParentId가 null이면 루트 이동을 허용한다")
	void moveAllowsRootMoveWhenTargetParentIsNull() {
		UUID workspaceId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		Document document = document(documentId, workspaceId, UUID.randomUUID(), "이동 대상", "00000000000000000001");
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));

		assertThatCode(() -> documentService.move(documentId, null, null, null, ACTOR_ID))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("성공_활성 부모 문서가 같은 워크스페이스에 있으면 이동 검증을 통과한다")
	void moveAllowsActiveParentInSameWorkspace() {
		UUID workspaceId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();
		Document document = document(documentId, workspaceId, null, "이동 대상", "00000000000000000001");
		Document parentDocument = parentDocument(parentId, workspaceId);
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(documentRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parentDocument));

		assertThatCode(() -> documentService.move(documentId, parentId, null, null, ACTOR_ID))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("성공_afterDocumentId만 있으면 해당 문서 뒤 위치로 sortKey 계산을 위임한다")
	void moveDelegatesSortKeyGenerationWhenAfterDocumentIdProvided() {
		UUID workspaceId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		UUID afterDocumentId = UUID.randomUUID();
		Document document = document(documentId, workspaceId, null, "이동 대상", "00000000000000000009");
		Document parentDocument = parentDocument(parentId, workspaceId);
		Document afterDocument = document(afterDocumentId, workspaceId, parentId, "앞 문서", "00000000000000000001");
		Document siblingDocument = document(UUID.randomUUID(), workspaceId, parentId, "다음 문서", "00000000000000000002");
		List<Document> siblings = List.of(afterDocument, siblingDocument, document);
		List<Document> targetSiblings = List.of(afterDocument, siblingDocument);
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(documentRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parentDocument));
		when(documentRepository.findActiveByWorkspaceIdAndParentIdOrderBySortKey(workspaceId, parentId)).thenReturn(
			siblings);
		when(orderedSortKeyGenerator.generate(eq(targetSiblings), any(), any(), eq(afterDocumentId), isNull()))
			.thenReturn("00000000000000000015");

		assertThatCode(() -> documentService.move(documentId, parentId, afterDocumentId, null, ACTOR_ID))
			.doesNotThrowAnyException();

		verify(orderedSortKeyGenerator).generate(eq(targetSiblings), any(), any(), eq(afterDocumentId), isNull());
	}

	@Test
	@DisplayName("성공_beforeDocumentId만 있으면 해당 문서 앞 위치로 sortKey 계산을 위임한다")
	void moveDelegatesSortKeyGenerationWhenBeforeDocumentIdProvided() {
		UUID workspaceId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		UUID beforeDocumentId = UUID.randomUUID();
		Document document = document(documentId, workspaceId, null, "이동 대상", "00000000000000000009");
		Document parentDocument = parentDocument(parentId, workspaceId);
		Document beforeDocument = document(beforeDocumentId, workspaceId, parentId, "뒤 문서", "00000000000000000002");
		Document siblingDocument = document(UUID.randomUUID(), workspaceId, parentId, "앞 문서", "00000000000000000001");
		List<Document> siblings = List.of(siblingDocument, beforeDocument, document);
		List<Document> targetSiblings = List.of(siblingDocument, beforeDocument);
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(documentRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parentDocument));
		when(documentRepository.findActiveByWorkspaceIdAndParentIdOrderBySortKey(workspaceId, parentId)).thenReturn(
			siblings);
		when(orderedSortKeyGenerator.generate(eq(targetSiblings), any(), any(), isNull(), eq(beforeDocumentId)))
			.thenReturn("00000000000000000001");

		assertThatCode(() -> documentService.move(documentId, parentId, null, beforeDocumentId, ACTOR_ID))
			.doesNotThrowAnyException();

		verify(orderedSortKeyGenerator).generate(eq(targetSiblings), any(), any(), isNull(), eq(beforeDocumentId));
	}

	@Test
	@DisplayName("성공_afterDocumentId와 beforeDocumentId가 없으면 대상 부모의 마지막 위치로 sortKey 계산을 위임한다")
	void moveDelegatesSortKeyGenerationWhenAnchorMissing() {
		UUID workspaceId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		Document document = document(documentId, workspaceId, null, "이동 대상", "00000000000000000009");
		Document parentDocument = parentDocument(parentId, workspaceId);
		Document siblingDocument = document(UUID.randomUUID(), workspaceId, parentId, "형제 문서", "00000000000000000002");
		List<Document> siblings = List.of(siblingDocument, document);
		List<Document> targetSiblings = List.of(siblingDocument);
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(documentRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parentDocument));
		when(documentRepository.findActiveByWorkspaceIdAndParentIdOrderBySortKey(workspaceId, parentId)).thenReturn(
			siblings);
		when(orderedSortKeyGenerator.generate(eq(targetSiblings), any(), any(), isNull(), isNull()))
			.thenReturn("00000000000000000003");

		assertThatCode(() -> documentService.move(documentId, parentId, null, null, ACTOR_ID))
			.doesNotThrowAnyException();

		verify(orderedSortKeyGenerator).generate(eq(targetSiblings), any(), any(), isNull(), isNull());
	}

	@Test
	@DisplayName("성공_afterDocumentId와 beforeDocumentId가 모두 있으면 두 문서 사이 위치로 sortKey 계산을 위임한다")
	void moveDelegatesSortKeyGenerationWhenBothAnchorsProvided() {
		UUID workspaceId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		UUID afterDocumentId = UUID.randomUUID();
		UUID beforeDocumentId = UUID.randomUUID();
		Document document = document(documentId, workspaceId, null, "이동 대상", "00000000000000000009");
		Document parentDocument = parentDocument(parentId, workspaceId);
		Document afterDocument = document(afterDocumentId, workspaceId, parentId, "앞 문서", "00000000000000000001");
		Document beforeDocument = document(beforeDocumentId, workspaceId, parentId, "뒤 문서", "00000000000000000002");
		List<Document> siblings = List.of(afterDocument, beforeDocument, document);
		List<Document> targetSiblings = List.of(afterDocument, beforeDocument);
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(documentRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parentDocument));
		when(documentRepository.findActiveByWorkspaceIdAndParentIdOrderBySortKey(workspaceId, parentId)).thenReturn(
			siblings);
		when(orderedSortKeyGenerator.generate(eq(targetSiblings), any(), any(), eq(afterDocumentId), eq(beforeDocumentId)))
			.thenReturn("000000000000000000015");

		assertThatCode(() -> documentService.move(documentId, parentId, afterDocumentId, beforeDocumentId, ACTOR_ID))
			.doesNotThrowAnyException();

		verify(orderedSortKeyGenerator).generate(eq(targetSiblings), any(), any(), eq(afterDocumentId), eq(beforeDocumentId));
	}

	@Test
	@DisplayName("성공_문서 이동 시 부모와 sortKey와 updatedBy를 함께 갱신한다")
	void moveUpdatesParentSortKeyAndUpdatedBy() {
		UUID workspaceId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		UUID targetParentId = UUID.randomUUID();
		Document document = document(documentId, workspaceId, null, "이동 대상", "00000000000000000009");
		Document targetParentDocument = parentDocument(targetParentId, workspaceId);
		java.util.List<Document> siblings = java.util.List.of(
			document(UUID.randomUUID(), workspaceId, targetParentId, "형제 문서", "00000000000000000001")
		);
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(documentRepository.findByIdAndDeletedAtIsNull(targetParentId)).thenReturn(Optional.of(targetParentDocument));
		when(documentRepository.findActiveByWorkspaceIdAndParentIdOrderBySortKey(workspaceId, targetParentId))
			.thenReturn(siblings);
		when(orderedSortKeyGenerator.generate(eq(siblings), any(), any(), isNull(), isNull()))
			.thenReturn("00000000000000000010");
		when(textNormalizer.normalizeNullable(" user-456 ")).thenReturn("user-456");

		documentService.move(documentId, targetParentId, null, null, " user-456 ");

		assertThat(document.getParentId()).isEqualTo(targetParentId);
		assertThat(document.getSortKey()).isEqualTo("00000000000000000010");
		assertThat(document.getUpdatedBy()).isEqualTo("user-456");
	}

	@Test
	@DisplayName("성공_루트 이동 시 parentId를 null로 바꾸고 sortKey와 updatedBy를 갱신한다")
	void moveUpdatesRootParentSortKeyAndUpdatedBy() {
		UUID workspaceId = UUID.randomUUID();
		UUID currentParentId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		Document document = document(documentId, workspaceId, currentParentId, "이동 대상", "00000000000000000009");
		List<Document> siblings = List.of(
			document(UUID.randomUUID(), workspaceId, null, "루트 문서", "00000000000000000001")
		);
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(documentRepository.findActiveByWorkspaceIdAndParentIdOrderBySortKey(workspaceId, null)).thenReturn(siblings);
		when(orderedSortKeyGenerator.generate(eq(siblings), any(), any(), isNull(), isNull()))
			.thenReturn("00000000000000000010");
		when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);

		documentService.move(documentId, null, null, null, ACTOR_ID);

		assertThat(document.getParentId()).isNull();
		assertThat(document.getSortKey()).isEqualTo("00000000000000000010");
		assertThat(document.getUpdatedBy()).isEqualTo(ACTOR_ID);
	}

	@Test
	@DisplayName("성공_같은 부모 내 reorder도 sortKey와 updatedBy를 갱신한다")
	void moveReordersWithinSameParent() {
		UUID workspaceId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		UUID afterDocumentId = UUID.randomUUID();
		Document document = document(documentId, workspaceId, parentId, "이동 대상", "00000000000000000009");
		Document parentDocument = parentDocument(parentId, workspaceId);
		java.util.List<Document> siblings = java.util.List.of(
			document(afterDocumentId, workspaceId, parentId, "앞 문서", "00000000000000000001"),
			document
		);
		List<Document> targetSiblings = List.of(
			siblings.get(0)
		);
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(documentRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parentDocument));
		when(documentRepository.findActiveByWorkspaceIdAndParentIdOrderBySortKey(workspaceId, parentId))
			.thenReturn(siblings);
		when(orderedSortKeyGenerator.generate(eq(targetSiblings), any(), any(), eq(afterDocumentId), isNull()))
			.thenReturn("00000000000000000015");
		when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);

		documentService.move(documentId, parentId, afterDocumentId, null, ACTOR_ID);

		assertThat(document.getParentId()).isEqualTo(parentId);
		assertThat(document.getSortKey()).isEqualTo("00000000000000000015");
		assertThat(document.getUpdatedBy()).isEqualTo(ACTOR_ID);
	}

	@Test
	@DisplayName("성공_동일 위치 no-op 요청은 문서를 갱신하지 않는다")
	void moveDoesNothingWhenTargetLocationIsSame() {
		UUID workspaceId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		Document document = document(documentId, workspaceId, parentId, "이동 대상", "00000000000000000009");
		document.setUpdatedBy("old-user");
		Document parentDocument = parentDocument(parentId, workspaceId);
		java.util.List<Document> siblings = java.util.List.of(document);
		List<Document> targetSiblings = List.of();
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(documentRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parentDocument));
		when(documentRepository.findActiveByWorkspaceIdAndParentIdOrderBySortKey(workspaceId, parentId))
			.thenReturn(siblings);
		when(orderedSortKeyGenerator.generate(eq(targetSiblings), any(), any(), isNull(), isNull()))
			.thenReturn("00000000000000000009");

		documentService.move(documentId, parentId, null, null, ACTOR_ID);

		assertThat(document.getParentId()).isEqualTo(parentId);
		assertThat(document.getSortKey()).isEqualTo("00000000000000000009");
		assertThat(document.getUpdatedBy()).isEqualTo("old-user");
		verify(textNormalizer, never()).normalizeNullable(ACTOR_ID);
	}

	@Test
	@DisplayName("실패_존재하지 않는 문서는 이동 시 문서 없음 예외를 던진다")
	void moveThrowsWhenDocumentMissing() {
		UUID documentId = UUID.randomUUID();

		assertThatThrownBy(() -> documentService.move(documentId, null, null, null, ACTOR_ID))
			.isInstanceOf(BusinessException.class)
			.hasMessage("요청한 문서를 찾을 수 없습니다.")
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.DOCUMENT_NOT_FOUND);
	}

	@Test
	@DisplayName("실패_대상 부모 문서가 다른 워크스페이스에 있으면 잘못된 요청 예외를 던진다")
	void moveThrowsWhenParentBelongsToOtherWorkspace() {
		UUID documentId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();
		Document document = document(documentId, UUID.randomUUID(), null, "이동 대상", "00000000000000000001");
		Document parentDocument = parentDocument(parentId, UUID.randomUUID());
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(documentRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parentDocument));

		assertThatThrownBy(() -> documentService.move(documentId, parentId, null, null, ACTOR_ID))
			.isInstanceOf(BusinessException.class)
			.hasMessage("잘못된 요청입니다.")
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.INVALID_REQUEST);
	}

	@Test
	@DisplayName("실패_자기 자신을 부모로 지정하면 잘못된 요청 예외를 던진다")
	void moveThrowsWhenParentIsSelf() {
		UUID documentId = UUID.randomUUID();
		Document document = document(documentId, UUID.randomUUID(), null, "이동 대상", "00000000000000000001");
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));

		assertThatThrownBy(() -> documentService.move(documentId, documentId, null, null, ACTOR_ID))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.INVALID_REQUEST);
	}

	@Test
	@DisplayName("실패_자신의 하위 문서를 부모로 지정하면 순환 이동 예외를 던진다")
	void moveThrowsWhenCycleDetected() {
		UUID workspaceId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		UUID childId = UUID.randomUUID();
		Document document = document(documentId, workspaceId, null, "루트 문서", "00000000000000000001");
		Document childDocument = document(childId, workspaceId, documentId, "하위 문서", "00000000000000000002");
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(documentRepository.findByIdAndDeletedAtIsNull(childId)).thenReturn(Optional.of(childDocument));

		assertThatThrownBy(() -> documentService.move(documentId, childId, null, null, ACTOR_ID))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.INVALID_REQUEST);
	}

	@Test
	@DisplayName("실패_afterDocumentId와 beforeDocumentId가 같은 삽입 간격을 가리키지 않으면 잘못된 요청 예외를 던진다")
	void moveThrowsWhenAnchorDocumentsAreContradictory() {
		UUID workspaceId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		UUID afterDocumentId = UUID.randomUUID();
		UUID beforeDocumentId = UUID.randomUUID();
		Document document = document(documentId, workspaceId, null, "이동 대상", "00000000000000000009");
		Document parentDocument = parentDocument(parentId, workspaceId);
		List<Document> siblings = List.of(
			document(afterDocumentId, workspaceId, parentId, "앞 문서", "00000000000000000001"),
			document(UUID.randomUUID(), workspaceId, parentId, "중간 문서", "00000000000000000002"),
			document(beforeDocumentId, workspaceId, parentId, "뒤 문서", "00000000000000000003"),
			document
		);
		List<Document> targetSiblings = List.of(
			siblings.get(0),
			siblings.get(1),
			siblings.get(2)
		);
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(documentRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parentDocument));
		when(documentRepository.findActiveByWorkspaceIdAndParentIdOrderBySortKey(workspaceId, parentId)).thenReturn(
			siblings);
		when(orderedSortKeyGenerator.generate(eq(targetSiblings), any(), any(), eq(afterDocumentId), eq(beforeDocumentId)))
			.thenThrow(new IllegalArgumentException("afterDocumentId와 beforeDocumentId는 같은 삽입 간격을 가리켜야 합니다."));

		assertThatThrownBy(
			() -> documentService.move(documentId, parentId, afterDocumentId, beforeDocumentId, ACTOR_ID))
			.isInstanceOf(BusinessException.class)
			.hasMessage("잘못된 요청입니다.")
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.INVALID_REQUEST);
	}

	@Test
	@DisplayName("실패_정렬 키 공간이 부족하면 재정렬 필요 예외를 던진다")
	void moveThrowsWhenSortKeySpaceExhausted() {
		UUID workspaceId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		Document document = document(documentId, workspaceId, null, "이동 대상", "00000000000000000009");
		Document parentDocument = parentDocument(parentId, workspaceId);
		List<Document> siblings = List.of(document(UUID.randomUUID(), workspaceId, parentId, "형제 문서",
			"00000000000000000001"), document);
		List<Document> targetSiblings = List.of(siblings.get(0));
		when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
		when(documentRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parentDocument));
		when(documentRepository.findActiveByWorkspaceIdAndParentIdOrderBySortKey(workspaceId, parentId)).thenReturn(
			siblings);
		when(orderedSortKeyGenerator.generate(eq(targetSiblings), any(), any(), isNull(), isNull()))
			.thenThrow(new OrderedSortKeyGenerator.SortKeyRebalanceRequiredException());

		assertThatThrownBy(() -> documentService.move(documentId, parentId, null, null, ACTOR_ID))
			.isInstanceOf(BusinessException.class)
			.hasMessage("정렬 키 공간이 부족하여 재정렬이 필요합니다.")
			.extracting("errorCode")
			.isEqualTo(BusinessErrorCode.SORT_KEY_REBALANCE_REQUIRED);
	}

	private Workspace workspace(UUID workspaceId) {
		return Workspace.builder()
			.id(workspaceId)
			.name("Docs Root")
			.build();
	}

	private Document document(UUID workspaceId, UUID parentId, String title, String sortKey) {
		return document(UUID.randomUUID(), workspaceId, parentId, title, sortKey);
	}

	private Document document(UUID documentId, UUID workspaceId, UUID parentId, String title, String sortKey) {
		return Document.builder()
			.id(documentId)
			.workspace(workspace(workspaceId))
			.parent(parentId == null ? null : parentDocument(parentId, workspaceId))
			.title(title)
			.sortKey(sortKey)
			.build();
	}

	private Document parentDocument(UUID documentId, UUID workspaceId) {
		return document(documentId, workspaceId, null, "부모 문서", "00000000000000000001");
	}

	private Document deletedDocument(UUID documentId, UUID workspaceId, UUID parentId, String title, String sortKey) {
		return deletedDocument(documentId, workspaceId, parentId, title, sortKey, LocalDateTime.now().minusMinutes(1));
	}

	private Document deletedDocument(
		UUID documentId,
		UUID workspaceId,
		UUID parentId,
		String title,
		String sortKey,
		LocalDateTime deletedAt
	) {
		Document document = document(documentId, workspaceId, parentId, title, sortKey);
		document.setDeletedAt(deletedAt);
		return document;
	}

}
