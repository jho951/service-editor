package com.documents.api.workspace.dto;

import com.documents.api.dto.BaseResponse;
import com.documents.domain.Workspace;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@Schema(description = "워크스페이스 응답")
public class WorkspaceResponse extends BaseResponse {

    @Schema(description = "워크스페이스 ID")
    private UUID id;

    @Schema(description = "워크스페이스 이름")
    private String name;

    @Schema(description = "생성자 식별자", nullable = true)
    private String createdBy;

    @Schema(description = "수정자 식별자", nullable = true)
    private String updatedBy;

    public static WorkspaceResponse from(Workspace workspace) {
        Long version = workspace.getVersion() == null ? null : workspace.getVersion().longValue();
        return WorkspaceResponse.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .createdBy(workspace.getCreatedBy())
                .updatedBy(workspace.getUpdatedBy())
                .version(version)
                .createdAt(workspace.getCreatedAt())
                .updatedAt(workspace.getUpdatedAt())
                .build();
    }
}
