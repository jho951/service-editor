package com.documents.boot;

import com.auditlog.api.AuditSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentsGovernanceAuditConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DocumentsGovernanceAuditConfiguration.class);

    @Bean
    public AuditSink documentsGovernanceAuditSink() {
        return event -> log.info(
            "governanceAudit eventId={} action={} resourceType={} resourceId={} result={} reason={} requestId={} traceId={}",
            event.getEventId(),
            event.getAction(),
            event.getResourceType(),
            event.getResourceId(),
            event.getResult(),
            event.getReason(),
            event.getRequestId(),
            event.getTraceId()
        );
    }
}
