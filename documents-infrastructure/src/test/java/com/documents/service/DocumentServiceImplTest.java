package com.documents.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.documents.domain.Document;
import com.documents.domain.Workspace;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.DocumentRepository;
import com.documents.support.DocumentSortKeyGenerator;
import com.documents.support.TextNormalizer;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Document 서비스 구현 검증")
class DocumentServiceImplTest {

    private static final String ACTOR_ID = "user-123";
    private static final String ROOT_TITLE = "프로젝트 개요";

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private TextNormalizer textNormalizer;

    @Mock
    private DocumentSortKeyGenerator documentSortKeyGenerator;

    @InjectMocks
    private DocumentServiceImpl documentService;

    @Test
    @DisplayName("성공_루트 문서 생성 시 워크스페이스와 사용자 식별자를 정규화하여 저장한다")
    void createRootDocumentNormalizesActorAndStoresDocument() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceService.getById(workspaceId)).thenReturn(workspace(workspaceId));
        when(textNormalizer.normalizeNullable(" " + ACTOR_ID + " ")).thenReturn(ACTOR_ID);
        when(textNormalizer.normalizeRequired("  " + ROOT_TITLE + "  ")).thenReturn(ROOT_TITLE);
        when(documentSortKeyGenerator.genNextSortKey(workspaceId, null)).thenReturn("00000000000000000001");
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
        assertThat(request.getSortKey()).isEqualTo("00000000000000000001");
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
        when(documentSortKeyGenerator.genNextSortKey(workspaceId, null)).thenReturn("00000000000000000001");
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
        when(documentSortKeyGenerator.genNextSortKey(workspaceId, parentId)).thenReturn("00000000000000000010");
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Document result = documentService.create(workspaceId, parentId, "하위 문서", null, null, ACTOR_ID);

        assertThat(result.getParentId()).isEqualTo(parentId);
        assertThat(result.getSortKey()).isEqualTo("00000000000000000010");
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
        Document rootDocument = Document.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .title("루트 문서")
                .sortKey("00000000000000000001")
                .build();
        Document childDocument = Document.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .parentId(rootDocument.getId())
                .title("하위 문서")
                .sortKey("00000000000000000002")
                .build();
        when(workspaceService.getById(workspaceId)).thenReturn(workspace(workspaceId));
        when(documentRepository.findActiveByWorkspaceIdOrderBySortKey(eq(workspaceId)))
                .thenReturn(java.util.List.of(rootDocument, childDocument));

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
        Document document = Document.builder()
                .id(documentId)
                .workspaceId(UUID.randomUUID())
                .title("프로젝트 개요")
                .sortKey("00000000000000000001")
                .build();
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
    @DisplayName("성공_문서 수정 시 제목은 trim 후 저장하고 updatedBy와 부모를 갱신한다")
    void updateDocumentTrimsTitleAndUpdatesAuditFields() {
        UUID workspaceId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Document document = Document.builder()
                .id(documentId)
                .workspaceId(workspaceId)
                .parentId(null)
                .title("기존 제목")
                .iconJson("{\"type\":\"emoji\",\"value\":\"😀\"}")
                .coverJson("{\"type\":\"image\",\"value\":\"cover-1\"}")
                .updatedBy("old-user")
                .sortKey("00000000000000000001")
                .build();
        when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.findByIdAndDeletedAtIsNull(parentId))
                .thenReturn(Optional.of(parentDocument(parentId, workspaceId)));
        when(textNormalizer.normalizeRequired("  수정된 제목  ")).thenReturn("수정된 제목");
        when(textNormalizer.normalizeNullable((String) null)).thenReturn(null);
        when(textNormalizer.normalizeNullable(" user-456 ")).thenReturn("user-456");

        Document result = documentService.update(
                documentId,
                "  수정된 제목  ",
                null,
                null,
                parentId,
                " user-456 "
        );

        assertThat(result).isSameAs(document);
        assertThat(result.getTitle()).isEqualTo("수정된 제목");
        assertThat(result.getParentId()).isEqualTo(parentId);
        assertThat(result.getIconJson()).isNull();
        assertThat(result.getCoverJson()).isNull();
        assertThat(result.getUpdatedBy()).isEqualTo("user-456");
    }

    @Test
    @DisplayName("성공_title이 null이면 기존 제목을 유지하고 parentId가 null이면 루트로 변경한다")
    void updateDocumentKeepsTitleWhenNullAndMovesToRoot() {
        UUID workspaceId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Document document = Document.builder()
                .id(documentId)
                .workspaceId(workspaceId)
                .parentId(UUID.randomUUID())
                .title("기존 제목")
                .iconJson("{\"type\":\"emoji\",\"value\":\"😀\"}")
                .coverJson("{\"type\":\"image\",\"value\":\"cover-1\"}")
                .updatedBy("old-user")
                .sortKey("00000000000000000001")
                .build();
        when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
        when(textNormalizer.normalizeNullable((String) null)).thenReturn(null);
        when(textNormalizer.normalizeNullable("{\"type\":\"emoji\",\"value\":\"📄\"}"))
                .thenReturn("{\"type\":\"emoji\",\"value\":\"📄\"}");
        when(textNormalizer.normalizeNullable(ACTOR_ID)).thenReturn(ACTOR_ID);

        Document result = documentService.update(documentId, null, "{\"type\":\"emoji\",\"value\":\"📄\"}", null, null, ACTOR_ID);

        assertThat(result.getTitle()).isEqualTo("기존 제목");
        assertThat(result.getParentId()).isNull();
        assertThat(result.getIconJson()).isEqualTo("{\"type\":\"emoji\",\"value\":\"📄\"}");
        assertThat(result.getCoverJson()).isNull();
        assertThat(result.getUpdatedBy()).isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("실패_빈 제목으로 수정하면 유효성 검사 예외를 던진다")
    void updateDocumentThrowsValidationExceptionWhenTitleBlank() {
        UUID documentId = UUID.randomUUID();
        Document document = Document.builder()
                .id(documentId)
                .workspaceId(UUID.randomUUID())
                .title("기존 제목")
                .sortKey("00000000000000000001")
                .build();
        when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
        when(textNormalizer.normalizeRequired("   ")).thenReturn("");

        assertThatThrownBy(() -> documentService.update(documentId, "   ", null, null, null, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("실패_자기 자신을 부모로 지정하면 잘못된 요청 예외를 던진다")
    void updateDocumentThrowsWhenParentIsSelf() {
        UUID documentId = UUID.randomUUID();
        Document document = Document.builder()
                .id(documentId)
                .workspaceId(UUID.randomUUID())
                .title("기존 제목")
                .sortKey("00000000000000000001")
                .build();
        when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> documentService.update(documentId, null, null, null, documentId, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_부모 문서가 다른 워크스페이스에 있으면 잘못된 요청 예외를 던진다")
    void updateDocumentThrowsWhenParentBelongsToOtherWorkspace() {
        UUID documentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Document document = Document.builder()
                .id(documentId)
                .workspaceId(UUID.randomUUID())
                .title("기존 제목")
                .sortKey("00000000000000000001")
                .build();
        when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.findByIdAndDeletedAtIsNull(parentId))
                .thenReturn(Optional.of(parentDocument(parentId, UUID.randomUUID())));

        assertThatThrownBy(() -> documentService.update(documentId, null, null, null, parentId, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패_순환 참조가 생기면 잘못된 요청 예외를 던진다")
    void updateDocumentThrowsWhenCycleDetected() {
        UUID workspaceId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        Document document = Document.builder()
                .id(documentId)
                .workspaceId(workspaceId)
                .title("루트 문서")
                .sortKey("00000000000000000001")
                .build();
        Document childDocument = Document.builder()
                .id(childId)
                .workspaceId(workspaceId)
                .parentId(documentId)
                .title("하위 문서")
                .sortKey("00000000000000000002")
                .build();
        when(documentRepository.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.findByIdAndDeletedAtIsNull(childId)).thenReturn(Optional.of(childDocument));

        assertThatThrownBy(() -> documentService.update(documentId, null, null, null, childId, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.INVALID_REQUEST);
    }

    private Workspace workspace(UUID workspaceId) {
        return Workspace.builder()
                .id(workspaceId)
                .name("Docs Root")
                .build();
    }

    private Document parentDocument(UUID documentId, UUID workspaceId) {
        return Document.builder()
                .id(documentId)
                .workspaceId(workspaceId)
                .title("부모 문서")
                .build();
    }
}
