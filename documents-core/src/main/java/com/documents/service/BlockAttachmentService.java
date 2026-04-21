package com.documents.service;

import java.io.InputStream;
import java.util.UUID;

import com.documents.service.attachment.BlockAttachmentContent;
import com.documents.service.attachment.BlockAttachmentDescriptor;

public interface BlockAttachmentService {

    BlockAttachmentDescriptor store(
        UUID blockId,
        String originalName,
        String contentType,
        Long contentLength,
        InputStream input,
        String actorId
    );

    BlockAttachmentDescriptor describe(UUID blockId, String attachmentId, String actorId);

    BlockAttachmentContent open(UUID blockId, String attachmentId, String actorId);

    void delete(UUID blockId, String attachmentId, String actorId);
}
