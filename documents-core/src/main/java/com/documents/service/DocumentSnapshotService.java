package com.documents.service;

import java.util.UUID;

import com.documents.service.snapshot.DocumentSnapshotContent;
import com.documents.service.snapshot.DocumentSnapshotDescriptor;

public interface DocumentSnapshotService {

    DocumentSnapshotDescriptor create(UUID documentId, String actorId);

    DocumentSnapshotDescriptor describe(UUID documentId, String snapshotId, String actorId);

    DocumentSnapshotContent open(UUID documentId, String snapshotId, String actorId);

    void delete(UUID documentId, String snapshotId, String actorId);
}
