package com.documents.boot;

import com.documents.service.DocumentResourceBindingService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-migration")
public class DocumentsResourceBackfillRunner implements CommandLineRunner {

    private final DocumentResourceBindingService documentResourceBindingService;

    public DocumentsResourceBackfillRunner(DocumentResourceBindingService documentResourceBindingService) {
        this.documentResourceBindingService = documentResourceBindingService;
    }

    @Override
    public void run(String... args) {
        documentResourceBindingService.reconcileBindings();
        documentResourceBindingService.purgePendingBindings();
    }
}
