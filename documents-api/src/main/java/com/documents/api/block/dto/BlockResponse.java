package com.documents.api.block.dto;

import com.documents.api.dto.BaseResponse;
import com.documents.domain.BlockType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@Schema(description = "블록 응답")
public class BlockResponse extends BaseResponse {

    @Schema(description = "블록 ID")
    private UUID id;

    @Schema(description = "문서 ID")
    private UUID documentId;

    @Schema(description = "부모 블록 ID", nullable = true)
    private UUID parentId;

    @Schema(description = "블록 타입")
    private BlockType type;

    @Schema(description = "TEXT 블록 본문")
    private String text;

    @Schema(description = "정렬 키")
    private String sortKey;

    @Schema(description = "생성자 식별자", nullable = true)
    private String createdBy;

    @Schema(description = "수정자 식별자", nullable = true)
    private String updatedBy;

    @Schema(description = "삭제 시각", nullable = true)
    private LocalDateTime deletedAt;
}
