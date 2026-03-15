package com.documents.api.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "워크스페이스 생성 요청")
public class CreateWorkspaceRequest {

    @NotBlank
    @Size(max = 100)
    @Schema(description = "워크스페이스 이름", example = "Team Workspace")
    private String name;
}
