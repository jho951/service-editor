package com.documents.service;

import com.documents.domain.Workspace;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.WorkspaceRepository;
import com.documents.support.TextNormalizer;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceServiceImpl implements WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final TextNormalizer textNormalizer;

    @Override
    @Transactional
    public Workspace create(String name, String actorId) {
        Workspace workspace = Workspace.builder()
                .id(UUID.randomUUID())
                .name(textNormalizer.normalizeRequired(name))
                .build();

        String normalizedActorId = textNormalizer.normalizeNullable(actorId);
        workspace.setCreatedBy(normalizedActorId);
        workspace.setUpdatedBy(normalizedActorId);

        return workspaceRepository.save(workspace);
    }

    @Override
    @Transactional(readOnly = true)
    public Workspace getById(UUID id) {
        return workspaceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.WORKSPACE_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UUID id) {
        return workspaceRepository.existsById(id);
    }
}
