package com.documents.api.auth;

public final class GatewayAuthContext {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_USER_ID_ATTRIBUTE = "gatewayAuthenticatedUserId";
    public static final String REQUEST_ID_ATTRIBUTE = "gatewayRequestId";
    public static final String REQUEST_START_AT_ATTRIBUTE = "gatewayRequestStartAt";

    private GatewayAuthContext() {
    }
}
