package com.documents;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.documents.service.DocumentService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DocumentTrashPurgeScheduler {

	private final DocumentService documentService;

	@Scheduled(fixedDelay = 60000)
	public void purgeExpiredTrash() {
		documentService.purgeExpiredTrash();
	}
}
