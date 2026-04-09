package com.documents.api.editor.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EditorSaveRequest {

    @NotBlank
    private String clientId;

    @NotBlank
    private String batchId;

    @Valid
    @NotEmpty
    private List<EditorSaveOperationRequest> operations;
}
