package com.documents.service;

import com.documents.domain.Workspace;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.WorkspaceRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WorkspaceServiceImpl implements WorkspaceService {

    private final WorkspaceRepository workspaceRepository;

    @Override
    @Transactional
    public Workspace create(String name, String actorId) {
        Workspace workspace = Workspace.builder()
                .id(UUID.randomUUID())
                .name(name.trim())
                .build();

        String normalizedActorId = StringUtils.hasText(actorId) ? actorId.trim() : null;
        workspace.setCreatedBy(normalizedActorId);
        workspace.setUpdatedBy(normalizedActorId);

        return workspaceRepository.save(workspace);
    }

    @Override
    @Transactional(readOnly = true)
    public Workspace getById(UUID id) {
        return workspaceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.RESOURCE_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UUID id) {
        return workspaceRepository.existsById(id);
    }
}
