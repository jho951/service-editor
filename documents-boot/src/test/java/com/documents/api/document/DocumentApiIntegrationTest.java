package com.documents.api.document;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

import com.documents.domain.Document;
import com.documents.domain.Workspace;
import com.documents.repository.DocumentRepository;
import com.documents.repository.WorkspaceRepository;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Document API 통합 검증")
class DocumentApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @BeforeEach
    void setUp() {
        documentRepository.deleteAll();
        workspaceRepository.deleteAll();
    }

    @Test
    @DisplayName("성공_문서 생성 API는 워크스페이스 하위에 루트 문서를 저장하고 응답한다")
    void createDocumentReturnsCreatedEnvelope() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());

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
                .andExpect(jsonPath("$.data.sortKey").value("00000000000000000001"))
                .andExpect(jsonPath("$.data.icon.type").value("emoji"))
                .andExpect(jsonPath("$.data.createdBy").value("user-123"))
                .andExpect(jsonPath("$.data.version").value(0));

        assertThat(documentRepository.findAll()).hasSize(1);
        Document savedDocument = documentRepository.findAll().get(0);
        assertThat(savedDocument.getWorkspaceId()).isEqualTo(workspace.getId());
        assertThat(savedDocument.getParentId()).isNull();
        assertThat(savedDocument.getTitle()).isEqualTo("프로젝트 개요");
        assertThat(savedDocument.getSortKey()).isEqualTo("00000000000000000001");
        assertThat(savedDocument.getIconJson()).isEqualTo("{\"type\":\"emoji\",\"value\":\"📄\"}");
        assertThat(savedDocument.getCreatedBy()).isEqualTo("user-123");
    }

    @Test
    @DisplayName("성공_워크스페이스 문서 목록 조회 API는 soft delete되지 않은 문서 목록을 반환한다")
    void getDocumentsReturnsDocumentList() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document rootDocument = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspace.getId())
                .title("루트 문서")
                .sortKey("00000000000000000001")
                .build());
        documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspace.getId())
                .title("삭제된 문서")
                .sortKey("00000000000000000099")
                .deletedAt(LocalDateTime.of(2026, 3, 16, 0, 0))
                .build());
        documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspace.getId())
                .parentId(rootDocument.getId())
                .title("하위 문서")
                .sortKey("00000000000000000002")
                .build());

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
    @DisplayName("실패_존재하지 않는 워크스페이스의 문서 목록 조회는 리소스 없음 응답을 반환한다")
    void getDocumentsReturnsNotFoundWhenWorkspaceMissing() throws Exception {
        var result = mockMvc.perform(get("/v1/workspaces/{workspaceId}/documents", UUID.randomUUID()));

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
                .workspaceId(otherWorkspace.getId())
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
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());

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
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());

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
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());

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
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspace.getId())
                .title("프로젝트 개요")
                .sortKey("00000000000000000001")
                .iconJson("{\"type\":\"emoji\",\"value\":\"📄\"}")
                .createdBy("user-123")
                .updatedBy("user-123")
                .build());

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
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspace.getId())
                .title("삭제된 문서")
                .sortKey("00000000000000000001")
                .deletedAt(LocalDateTime.of(2026, 3, 16, 0, 0))
                .build());

        var result = mockMvc.perform(get("/v1/documents/{documentId}", document.getId()));

        assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("성공_문서 수정 API는 제목 trim, 부모 변경, updatedBy 갱신을 반영한다")
    void updateDocumentPersistsServiceLogic() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document parentDocument = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspace.getId())
                .title("부모 문서")
                .sortKey("00000000000000000001")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspace.getId())
                .title("기존 제목")
                .sortKey("00000000000000000002")
                .iconJson("{\"type\":\"emoji\",\"value\":\"😀\"}")
                .coverJson("{\"type\":\"image\",\"value\":\"cover-1\"}")
                .updatedBy("user-123")
                .build());

        mockMvc.perform(patch("/v1/documents/{documentId}", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "title": "  수정된 제목  ",
                                  "parentId": "%s",
                                  "icon": null,
                                  "cover": null
                                }
                                """.formatted(parentDocument.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 제목"))
                .andExpect(jsonPath("$.data.parentId").value(parentDocument.getId().toString()))
                .andExpect(jsonPath("$.data.icon").doesNotExist())
                .andExpect(jsonPath("$.data.cover").doesNotExist())
                .andExpect(jsonPath("$.data.updatedBy").value("user-456"))
                .andExpect(jsonPath("$.data.version").value(1));

        Document updatedDocument = documentRepository.findById(document.getId()).orElseThrow();
        assertThat(updatedDocument.getTitle()).isEqualTo("수정된 제목");
        assertThat(updatedDocument.getParentId()).isEqualTo(parentDocument.getId());
        assertThat(updatedDocument.getIconJson()).isNull();
        assertThat(updatedDocument.getCoverJson()).isNull();
        assertThat(updatedDocument.getUpdatedBy()).isEqualTo("user-456");
    }

    @Test
    @DisplayName("실패_빈 제목으로 문서를 수정하면 유효성 검사 오류를 반환한다")
    void updateDocumentReturnsValidationErrorWhenTitleBlank() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspace.getId())
                .title("기존 제목")
                .sortKey("00000000000000000001")
                .build());

        var result = mockMvc.perform(patch("/v1/documents/{documentId}", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "title": "   "
                                }
                                """));

        assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
    }

    @Test
    @DisplayName("실패_존재하지 않는 문서를 수정하면 리소스 없음 응답을 반환한다")
    void updateDocumentReturnsNotFoundWhenDocumentMissing() throws Exception {
        var result = mockMvc.perform(patch("/v1/documents/{documentId}", UUID.randomUUID())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "title": "수정된 제목"
                                }
                                """));

        assertErrorEnvelope(result, "NOT_FOUND", 9004, "요청한 문서를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("실패_다른 워크스페이스 부모 문서를 지정하면 잘못된 요청 응답을 반환한다")
    void updateDocumentReturnsBadRequestWhenParentBelongsToOtherWorkspace() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Workspace otherWorkspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Other Workspace")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspace.getId())
                .title("기존 제목")
                .sortKey("00000000000000000001")
                .build());
        Document otherWorkspaceParent = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspaceId(otherWorkspace.getId())
                .title("다른 워크스페이스 문서")
                .sortKey("00000000000000000001")
                .build());

        var result = mockMvc.perform(patch("/v1/documents/{documentId}", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "parentId": "%s"
                                }
                                """.formatted(otherWorkspaceParent.getId())));

        assertErrorEnvelope(result, "BAD_REQUEST", 9015, "잘못된 요청입니다.");
    }

    @Test
    @DisplayName("실패_자기 자신을 부모로 지정하면 잘못된 요청 응답을 반환한다")
    void updateDocumentReturnsBadRequestWhenParentIsSelf() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspace.getId())
                .title("기존 제목")
                .sortKey("00000000000000000001")
                .build());

        var result = mockMvc.perform(patch("/v1/documents/{documentId}", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "parentId": "%s"
                                }
                                """.formatted(document.getId())));

        assertErrorEnvelope(result, "BAD_REQUEST", 9015, "잘못된 요청입니다.");
    }

    @Test
    @DisplayName("실패_하위 문서를 부모로 올려 순환 참조가 생기면 잘못된 요청 오류를 반환한다")
    void updateDocumentReturnsBadRequestWhenCycleDetected() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document rootDocument = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspace.getId())
                .title("루트 문서")
                .sortKey("00000000000000000001")
                .build());
        Document childDocument = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspace.getId())
                .parentId(rootDocument.getId())
                .title("하위 문서")
                .sortKey("00000000000000000002")
                .build());

        var result = mockMvc.perform(patch("/v1/documents/{documentId}", rootDocument.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "parentId": "%s"
                                }
                                """.formatted(childDocument.getId())));

        assertErrorEnvelope(result, "BAD_REQUEST", 9015, "잘못된 요청입니다.");
    }

    private void assertErrorEnvelope(ResultActions result, String httpStatus, int code, String message) throws Exception {
        result.andExpect(status().is(org.springframework.http.HttpStatus.valueOf(httpStatus).value()))
                .andExpect(jsonPath("$.httpStatus").value(httpStatus))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
