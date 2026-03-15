package com.documents.api.workspace;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.documents.api.exception.GlobalExceptionHandler;
import com.documents.domain.Workspace;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.service.WorkspaceService;
import java.time.LocalDateTime;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("Workspace 컨트롤러 빠른 검증")
class WorkspaceControllerWebMvcTest {

    @Mock
    private WorkspaceService workspaceService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new WorkspaceController(workspaceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("성공_워크스페이스 생성 요청에 대해 생성 응답을 반환한다")
    void createWorkspaceReturnsCreatedEnvelope() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceService.create(eq("Team Workspace"), eq("user-123")))
                .thenReturn(workspace(workspaceId, "Team Workspace", "user-123", 0));

        mockMvc.perform(post("/v1/workspaces")
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "name": "Team Workspace"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.id").value(workspaceId.toString()))
                .andExpect(jsonPath("$.data.name").value("Team Workspace"))
                .andExpect(jsonPath("$.data.createdBy").value("user-123"));
    }

    @Test
    @DisplayName("실패_워크스페이스 이름이 공백이면 유효성 검사 오류를 반환한다")
    void createWorkspaceRejectsBlankName() throws Exception {
        mockMvc.perform(post("/v1/workspaces")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(9016));
    }

    @Test
    @DisplayName("실패_워크스페이스 이름이 누락되면 유효성 검사 오류를 반환한다")
    void createWorkspaceRejectsMissingName() throws Exception {
        mockMvc.perform(post("/v1/workspaces")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(9016));
    }

    @Test
    @DisplayName("실패_워크스페이스 이름 길이가 제한을 초과하면 유효성 검사 오류를 반환한다")
    void createWorkspaceRejectsTooLongName() throws Exception {
        String overLimitName = "a".repeat(101);

        mockMvc.perform(post("/v1/workspaces")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "%s"
                                }
                                """.formatted(overLimitName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(9016));
    }

    @Test
    @DisplayName("실패_존재하지 않는 워크스페이스를 조회하면 리소스 없음 응답을 반환한다")
    void getWorkspaceReturnsNotFoundEnvelope() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceService.getById(workspaceId))
                .thenThrow(new BusinessException(BusinessErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/v1/workspaces/{workspaceId}", workspaceId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(9003));
    }

    private Workspace workspace(UUID id, String name, String actorId, Integer version) {
        Workspace workspace = Workspace.builder()
                .id(id)
                .name(name)
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();
        workspace.setCreatedAt(LocalDateTime.of(2026, 3, 16, 0, 0));
        workspace.setUpdatedAt(LocalDateTime.of(2026, 3, 16, 0, 0));
        workspace.setVersion(version);
        return workspace;
    }
}
