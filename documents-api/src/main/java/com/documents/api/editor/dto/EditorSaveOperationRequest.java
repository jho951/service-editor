package com.documents.api.editor.dto;

import com.documents.api.block.validation.ValidBlockContent;
import com.documents.service.editor.EditorSaveOperationType;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EditorSaveOperationRequest {

    @NotBlank
    private String opId;

    @NotNull
    private EditorSaveOperationType type;

    private String blockRef;

    private Long version;

    @ValidBlockContent
    private JsonNode content;

    private String parentRef;

    private String afterRef;

    private String beforeRef;

    @AssertTrue
    public boolean isValidOperationShape() {
        if (type == null) {
            return true;
        }

        if (type == EditorSaveOperationType.BLOCK_CREATE) {
            return hasText(blockRef)
                    && version == null;
        }

        if (type == EditorSaveOperationType.BLOCK_REPLACE_CONTENT) {
            return hasText(blockRef)
                    && hasContentValue()
                    && parentRef == null
                    && afterRef == null
                    && beforeRef == null;
        }

        if (type == EditorSaveOperationType.BLOCK_DELETE) {
            return hasText(blockRef)
                    && !hasContentValue()
                    && parentRef == null
                    && afterRef == null
                    && beforeRef == null;
        }

        if (type == EditorSaveOperationType.BLOCK_MOVE) {
            return hasText(blockRef)
                    && !hasContentValue();
        }

        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasContentValue() {
        return content != null && !content.isNull();
    }
}
