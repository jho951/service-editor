package com.documents.api.workspace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.documents.domain.Workspace;
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
@DisplayName("Workspace API 통합 검증")
class WorkspaceApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @BeforeEach
    void setUp() {
        workspaceRepository.deleteAll();
    }

    @Test
    @DisplayName("성공_워크스페이스 생성 API는 애플리케이션 조립 상태에서 생성 응답을 반환한다")
    void createWorkspaceReturnsCreatedEnvelope() throws Exception {
        String requestBody = """
                {
                  "name": "Team Workspace"
                }
                """;

        mockMvc.perform(post("/v1/workspaces")
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("리소스 생성 성공"))
                .andExpect(jsonPath("$.data.name").value("Team Workspace"))
                .andExpect(jsonPath("$.data.createdBy").value("user-123"))
                .andExpect(jsonPath("$.data.updatedBy").value("user-123"))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.version").value(0));
    }

    @Test
    @DisplayName("성공_워크스페이스 조회 API는 저장된 리소스를 애플리케이션 조립 상태에서 반환한다")
    void getWorkspaceReturnsWorkspaceEnvelope() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());

        mockMvc.perform(get("/v1/workspaces/{workspaceId}", workspace.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(workspace.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Docs Root"));
    }

    @Test
    @DisplayName("실패_워크스페이스 생성 API는 공백 이름 요청에 대해 유효성 검사 오류를 반환한다")
    void createWorkspaceReturnsValidationErrorWhenNameIsBlank() throws Exception {
        String requestBody = """
                {
                  "name": " "
                }
                """;

        mockMvc.perform(post("/v1/workspaces")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(9016))
                .andExpect(jsonPath("$.message").value("요청 필드 유효성 검사에 실패했습니다."));
    }
}
