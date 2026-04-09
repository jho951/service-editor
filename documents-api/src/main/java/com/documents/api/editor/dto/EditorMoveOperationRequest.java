package com.documents.api.editor.dto;

import java.util.UUID;

import com.documents.service.editor.EditorMoveResourceType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "에디터 이동 요청")
public class EditorMoveOperationRequest {

    @NotNull
    @Schema(description = "이동 대상 리소스 종류", example = "DOCUMENT", nullable = false)
    private EditorMoveResourceType resourceType;

    @NotNull
    @Schema(description = "이동 대상 리소스 ID", nullable = false)
    private UUID resourceId;

    @Schema(description = "이동 대상 부모 리소스 ID", nullable = true)
    private UUID targetParentId;

    @Schema(description = "같은 형제 집합의 앞 리소스 ID", nullable = true)
    private UUID afterId;

    @Schema(description = "같은 형제 집합의 뒤 리소스 ID", nullable = true)
    private UUID beforeId;

    @Schema(description = "클라이언트가 기준으로 삼은 블록 버전", example = "3", nullable = true)
    private Long version;

    @AssertTrue(message = "resourceType이 BLOCK이면 version이 필요합니다.")
    public boolean hasVersionWhenBlockMove() {
        return resourceType != EditorMoveResourceType.BLOCK || version != null;
    }
}
