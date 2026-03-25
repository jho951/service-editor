package com.documents.api.document.dto;

import com.documents.api.dto.BaseResponse;
import com.documents.domain.DocumentVisibility;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@Schema(description = "문서 응답")
public class DocumentResponse extends BaseResponse {

    @Schema(description = "문서 ID")
    private UUID id;

    @Schema(description = "워크스페이스 ID")
    private UUID workspaceId;

    @Schema(description = "부모 문서 ID", nullable = true)
    private UUID parentId;

    @Schema(description = "문서 제목")
    private String title;

    @Schema(description = "문서 아이콘 JSON", nullable = true)
    private JsonNode icon;

    @Schema(description = "문서 커버 JSON", nullable = true)
    private JsonNode cover;

    @Schema(description = "문서 공개 상태")
    private DocumentVisibility visibility;

    @Schema(description = "정렬 키", nullable = true)
    private String sortKey;

    @Schema(description = "생성자 식별자", nullable = true)
    private String createdBy;

    @Schema(description = "수정자 식별자", nullable = true)
    private String updatedBy;

    @Schema(description = "삭제 시각", nullable = true)
    private LocalDateTime deletedAt;
}
