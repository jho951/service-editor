package com.documents.api.block.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "블록 이동 요청")
public class MoveBlockRequest {

    @Schema(description = "이동 대상 부모 블록 ID", nullable = true)
    private UUID parentId;

    @Schema(description = "같은 형제 집합의 앞 블록 ID", nullable = true)
    private UUID afterBlockId;

    @Schema(description = "같은 형제 집합의 뒤 블록 ID", nullable = true)
    private UUID beforeBlockId;

    @NotNull
    @Schema(description = "클라이언트가 기준으로 삼은 블록 버전", example = "3", nullable = false)
    private Integer version;
}
