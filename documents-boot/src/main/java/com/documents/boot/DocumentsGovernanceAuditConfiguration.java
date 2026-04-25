package com.documents.boot;

import io.github.jho951.platform.governance.api.GovernanceAuditSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentsGovernanceAuditConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DocumentsGovernanceAuditConfiguration.class);

    @Bean
    public GovernanceAuditSink documentsGovernanceAuditSink() {
        return entry -> log.info(
            "governanceAudit category={} message={} requestId={} traceId={}",
            entry.category(),
            entry.message(),
            entry.attributes().getOrDefault("requestId", ""),
            entry.attributes().getOrDefault("traceId", "")
        );
    }
}
