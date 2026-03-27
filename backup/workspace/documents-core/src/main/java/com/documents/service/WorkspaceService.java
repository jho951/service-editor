package com.documents.service;

import com.documents.domain.Workspace;
import java.util.UUID;

public interface WorkspaceService {
    Workspace create(String name, String actorId);
    Workspace getById(UUID id);
    boolean existsById(UUID id);
}
