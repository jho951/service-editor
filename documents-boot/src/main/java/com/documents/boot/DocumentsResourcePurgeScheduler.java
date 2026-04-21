package com.documents.boot;

import com.documents.service.DocumentResourceBindingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DocumentsResourcePurgeScheduler {

    private final DocumentResourceBindingService documentResourceBindingService;

    public DocumentsResourcePurgeScheduler(DocumentResourceBindingService documentResourceBindingService) {
        this.documentResourceBindingService = documentResourceBindingService;
    }

    @Scheduled(fixedDelayString = "${documents.resource.purge.fixed-delay-ms:60000}")
    public void purgePendingBindings() {
        documentResourceBindingService.purgePendingBindings();
    }
}
