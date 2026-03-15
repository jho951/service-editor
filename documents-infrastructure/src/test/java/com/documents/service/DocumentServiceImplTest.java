package com.documents.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.documents.domain.Document;
import com.documents.domain.Workspace;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.DocumentRepository;
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

    @InjectMocks
    private DocumentServiceImpl documentService;

    @Test
    @DisplayName("성공_루트 문서 생성 시 워크스페이스와 사용자 식별자를 정규화하여 저장한다")
    void createRootDocumentNormalizesActorAndStoresDocument() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceService.getById(workspaceId)).thenReturn(workspace(workspaceId));
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
        assertThat(request.getCreatedBy()).isEqualTo(ACTOR_ID);
        assertThat(request.getUpdatedBy()).isEqualTo(ACTOR_ID);
        assertThat(result).isSameAs(request);
    }

    @Test
    @DisplayName("성공_사용자 식별자 헤더가 공백이면 감사 필드를 null로 저장한다")
    void createDocumentStoresNullActorWhenHeaderIsBlank() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceService.getById(workspaceId)).thenReturn(workspace(workspaceId));
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
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Document result = documentService.create(workspaceId, parentId, "하위 문서", null, null, ACTOR_ID);

        assertThat(result.getParentId()).isEqualTo(parentId);
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
