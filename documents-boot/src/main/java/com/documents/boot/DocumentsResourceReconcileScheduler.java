package com.documents.boot;

import com.documents.service.DocumentResourceBindingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DocumentsResourceReconcileScheduler {

    private final DocumentResourceBindingService documentResourceBindingService;

    public DocumentsResourceReconcileScheduler(DocumentResourceBindingService documentResourceBindingService) {
        this.documentResourceBindingService = documentResourceBindingService;
    }

    @Scheduled(fixedDelayString = "${documents.resource.reconcile.fixed-delay-ms:3600000}")
    public void reconcileBindings() {
        documentResourceBindingService.reconcileBindings();
    }
}
