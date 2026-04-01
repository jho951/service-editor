package com.documents.api.audit;

import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.auditlog.api.AuditActorType;
import com.auditlog.api.AuditEvent;
import com.auditlog.api.AuditEventType;
import com.auditlog.api.AuditLogger;
import com.auditlog.api.AuditResult;
import com.auditlog.api.AuditSink;
import com.auditlog.core.DefaultAuditLogger;
import com.auditlog.core.FileAuditSink;

@Service
public class BlockAuditLogService {

    private final boolean enabled;
    private final AuditLogger logger;

    public BlockAuditLogService(
            @Value("${auditlog.enabled:true}") boolean enabled,
            @Value("${auditlog.service-name:documents-app}") String serviceName,
            @Value("${auditlog.env:dev}") String env,
            @Value("${auditlog.file-path:./logs/audit.log}") String filePath
    ) {
        this.enabled = enabled;
        if (!enabled) {
            this.logger = null;
            return;
        }

        AuditSink sink = new FileAuditSink(Path.of(filePath), serviceName, env);
        this.logger = new DefaultAuditLogger(sink, List.of(), null);
    }

    public void logRequest(String method, String path, String requestId, String userId, int status, long durationMs, String reason) {
        if (!enabled || logger == null) {
            return;
        }

        AuditEvent event = AuditEvent.builder(AuditEventType.CUSTOM, "EDITOR_GATEWAY_REQUEST")
                .actor(valueOrDash(userId), userId == null || userId.isBlank() ? AuditActorType.ANONYMOUS : AuditActorType.USER, null)
                .resource("HTTP_PATH", valueOrDash(path))
                .result(status < 400 ? AuditResult.SUCCESS : AuditResult.FAILURE)
                .reason(valueOrDash(reason))
                .traceId(valueOrDash(requestId))
                .requestId(valueOrDash(requestId))
                .detail("method", valueOrDash(method))
                .detail("status", String.valueOf(status))
                .detail("durationMs", String.valueOf(durationMs))
                .build();
        logger.log(event);
    }

    private String valueOrDash(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }
}
