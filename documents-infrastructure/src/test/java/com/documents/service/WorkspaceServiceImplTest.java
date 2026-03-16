package com.documents.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.documents.domain.Workspace;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.WorkspaceRepository;
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
@DisplayName("Workspace 서비스 구현 검증")
class WorkspaceServiceImplTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private TextNormalizer textNormalizer;

    @InjectMocks
    private WorkspaceServiceImpl workspaceService;

    @Test
    @DisplayName("성공_워크스페이스 생성 시 이름과 사용자 식별자를 정규화하여 저장한다")
    void createNormalizesNameAndActorId() {
        Workspace saved = Workspace.builder()
                .id(UUID.randomUUID())
                .name("Team Workspace")
                .createdBy("user-123")
                .updatedBy("user-123")
                .build();
        when(textNormalizer.normalizeRequired("  Team Workspace  ")).thenReturn("Team Workspace");
        when(textNormalizer.normalizeNullable(" user-123 ")).thenReturn("user-123");
        when(workspaceRepository.save(any(Workspace.class))).thenReturn(saved);

        Workspace result = workspaceService.create("  Team Workspace  ", " user-123 ");

        ArgumentCaptor<Workspace> captor = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaceRepository).save(captor.capture());
        Workspace request = captor.getValue();

        assertThat(request.getId()).isNotNull();
        assertThat(request.getName()).isEqualTo("Team Workspace");
        assertThat(request.getCreatedBy()).isEqualTo("user-123");
        assertThat(request.getUpdatedBy()).isEqualTo("user-123");
        assertThat(result).isSameAs(saved);
    }

    @Test
    @DisplayName("성공_사용자 식별자 헤더가 공백이면 감사 필드를 null로 저장한다")
    void createStoresNullActorWhenHeaderIsBlank() {
        when(textNormalizer.normalizeRequired("Team Workspace")).thenReturn("Team Workspace");
        when(textNormalizer.normalizeNullable(" ")).thenReturn(null);
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Workspace result = workspaceService.create("Team Workspace", " ");

        assertThat(result.getCreatedBy()).isNull();
        assertThat(result.getUpdatedBy()).isNull();
    }

    @Test
    @DisplayName("성공_워크스페이스가 존재하면 단건 조회 결과를 반환한다")
    void getByIdReturnsWorkspaceWhenPresent() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = Workspace.builder()
                .id(workspaceId)
                .name("Docs Root")
                .build();
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        Workspace result = workspaceService.getById(workspaceId);

        assertThat(result).isSameAs(workspace);
    }

    @Test
    @DisplayName("실패_워크스페이스가 존재하지 않으면 단건 조회 시 예외를 던진다")
    void getByIdThrowsWhenMissing() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.getById(workspaceId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("요청한 워크스페이스를 찾을 수 없습니다.")
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.WORKSPACE_NOT_FOUND);
    }

    @Test
    @DisplayName("성공_워크스페이스 존재 여부 조회는 저장소 결과를 그대로 반환한다")
    void existsByIdDelegatesToRepository() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);

        boolean result = workspaceService.existsById(workspaceId);

        assertThat(result).isTrue();
        verify(workspaceRepository).existsById(workspaceId);
    }
}
