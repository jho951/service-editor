package com.documents.api.workspace;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.documents.api.code.SuccessCode;
import com.documents.api.dto.GlobalResponse;
import com.documents.api.workspace.dto.CreateWorkspaceRequest;
import com.documents.api.workspace.dto.WorkspaceResponse;
import com.documents.domain.Workspace;
import com.documents.service.WorkspaceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Workspace", description = "워크스페이스 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/workspaces")
public class WorkspaceController {

    // TODO: 임시 헤더 값. 인증 서버와 연동 후 수정 필요
    private static final String USER_ID_HEADER = "X-User-Id";

    private final WorkspaceService workspaceService;

    @Operation(summary = "워크스페이스 생성")
    @PostMapping
    public ResponseEntity<GlobalResponse<WorkspaceResponse>> createWorkspace(
            @Valid @RequestBody CreateWorkspaceRequest request,
            @RequestHeader(USER_ID_HEADER) String userId
    ) {
        Workspace createdWorkspace = workspaceService.create(request.getName(), userId);
        WorkspaceResponse response = WorkspaceResponse.from(createdWorkspace);

        return ResponseEntity.status(SuccessCode.CREATED.getHttpStatus())
                .body(GlobalResponse.ok(SuccessCode.CREATED, response));
    }

    @Operation(summary = "워크스페이스 단건 조회")
    @GetMapping("/{workspaceId}")
    public ResponseEntity<GlobalResponse<WorkspaceResponse>> getWorkspace(
            @PathVariable("workspaceId") UUID workspaceId
    ) {
        Workspace workspace = workspaceService.getById(workspaceId);
        return ResponseEntity.ok(GlobalResponse.ok(SuccessCode.SUCCESS, WorkspaceResponse.from(workspace)));
    }
}
