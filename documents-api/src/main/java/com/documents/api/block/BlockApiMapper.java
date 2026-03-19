package com.documents.api.block;

import com.documents.api.block.dto.BlockResponse;
import com.documents.api.block.support.BlockJsonCodec;
import com.documents.domain.Block;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BlockApiMapper {

    private final BlockJsonCodec blockJsonCodec;

    public BlockResponse toResponse(Block block) {
        Long version = block.getVersion() == null ? null : block.getVersion().longValue();
        return BlockResponse.builder()
                .id(block.getId())
                .documentId(block.getDocumentId())
                .parentId(block.getParentId())
                .type(block.getType())
                .content(blockJsonCodec.read(block.getContent()))
                .text(block.getText())
                .sortKey(block.getSortKey())
                .createdBy(block.getCreatedBy())
                .updatedBy(block.getUpdatedBy())
                .deletedAt(block.getDeletedAt())
                .version(version)
                .createdAt(block.getCreatedAt())
                .updatedAt(block.getUpdatedAt())
                .build();
    }
}
