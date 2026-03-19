package com.documents.api.block.dto;

import com.documents.api.block.validation.ValidBlockContent;
import com.documents.domain.BlockType;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "블록 생성 요청")
public class CreateBlockRequest {

    @Schema(description = "부모 블록 ID", nullable = true)
    private UUID parentId;

    @NotNull
    @Schema(description = "블록 타입", example = "TEXT")
    private BlockType type;

    @NotNull
    @ValidBlockContent
    @Schema(description = "TEXT 블록 content", nullable = false)
    private JsonNode content;

    @Schema(description = "기준 블록 뒤에 삽입할 블록 ID", nullable = true)
    private UUID afterBlockId;

    @Schema(description = "기준 블록 앞에 삽입할 블록 ID", nullable = true)
    private UUID beforeBlockId;
}
