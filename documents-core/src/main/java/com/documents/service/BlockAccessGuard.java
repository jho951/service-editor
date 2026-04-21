package com.documents.service;

import java.util.UUID;

import com.documents.domain.Block;

public interface BlockAccessGuard {
    Block requireReadable(UUID blockId, String actorId);
    Block requireWritable(UUID blockId, String actorId);
}
