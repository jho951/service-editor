package com.documents.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.documents.domain.Document;
import com.documents.domain.Workspace;
import com.documents.repository.DocumentRepository;
import com.documents.repository.WorkspaceRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

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
                .andExpect(jsonPath("$.data.icon.type").value("emoji"))
                .andExpect(jsonPath("$.data.createdBy").value("user-123"))
                .andExpect(jsonPath("$.data.version").value(0));

        assertThat(documentRepository.findAll()).hasSize(1);
        Document savedDocument = documentRepository.findAll().get(0);
        assertThat(savedDocument.getWorkspaceId()).isEqualTo(workspace.getId());
        assertThat(savedDocument.getParentId()).isNull();
        assertThat(savedDocument.getTitle()).isEqualTo("프로젝트 개요");
        assertThat(savedDocument.getIconJson()).isEqualTo("{\"type\":\"emoji\",\"value\":\"📄\"}");
        assertThat(savedDocument.getCreatedBy()).isEqualTo("user-123");
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

    private void assertErrorEnvelope(org.springframework.test.web.servlet.ResultActions result, String httpStatus, int code, String message) throws Exception {
        result.andExpect(status().is(org.springframework.http.HttpStatus.valueOf(httpStatus).value()))
                .andExpect(jsonPath("$.httpStatus").value(httpStatus))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
