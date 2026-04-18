package com.documents.api.auth;

import java.util.UUID;

import io.github.jho951.platform.governance.api.AuditEntry;
import io.github.jho951.platform.governance.api.AuditLogRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import com.documents.api.code.ErrorCode;
import com.documents.api.exception.GlobalException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class GatewayAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthInterceptor.class);
    private final AuditLogRecorder auditLogRecorder;

    public GatewayAuthInterceptor(AuditLogRecorder auditLogRecorder) {
        this.auditLogRecorder = auditLogRecorder;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        long startedAt = System.currentTimeMillis();
        request.setAttribute(GatewayAuthContext.REQUEST_START_AT_ATTRIBUTE, startedAt);

        String requestId = normalize(request.getHeader(GatewayAuthContext.REQUEST_ID_HEADER));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        request.setAttribute(GatewayAuthContext.REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(GatewayAuthContext.REQUEST_ID_HEADER, requestId);

        String userId = normalize(request.getHeader(GatewayAuthContext.USER_ID_HEADER));
        if (userId == null) {
            log.warn(
                "gateway auth rejected method={} path={} status=401 reason=missing_or_blank_user_id requestId={}",
                request.getMethod(),
                request.getRequestURI(),
                requestId
            );
            throw new GlobalException(ErrorCode.UNAUTHORIZED);
        }

        request.setAttribute(GatewayAuthContext.REQUEST_USER_ID_ATTRIBUTE, userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Long startedAt = (Long)request.getAttribute(GatewayAuthContext.REQUEST_START_AT_ATTRIBUTE);
        long durationMs = startedAt == null ? 0L : System.currentTimeMillis() - startedAt;

        String requestId = stringAttribute(request, GatewayAuthContext.REQUEST_ID_ATTRIBUTE);
        String userId = stringAttribute(request, GatewayAuthContext.REQUEST_USER_ID_ATTRIBUTE);

        log.info(
            "gateway audit method={} path={} status={} userId={} requestId={} durationMs={}",
            request.getMethod(),
            request.getRequestURI(),
            response.getStatus(),
            userId == null ? "-" : userId,
            requestId == null ? "-" : requestId,
            durationMs
        );
        auditLogRecorder.record(new AuditEntry(
            "documents",
            "DOCUMENTS_API_REQUEST",
            java.util.Map.of(
                "eventType", "READ",
                "actorId", userId == null ? "anonymous" : userId,
                "actorType", userId == null ? "ANONYMOUS" : "USER",
                "resourceType", "HTTP_PATH",
                "resourceId", request.getRequestURI(),
                "result", response.getStatus() < 400 ? "SUCCESS" : "FAILURE",
                "requestId", requestId == null ? "" : requestId,
                "method", request.getMethod(),
                "status", String.valueOf(response.getStatus()),
                "durationMs", String.valueOf(durationMs)
            ),
            java.time.Instant.now()
        ));
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String stringAttribute(HttpServletRequest request, String attributeName) {
        Object value = request.getAttribute(attributeName);
        return value instanceof String stringValue ? stringValue : null;
    }
}
