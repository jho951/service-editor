package com.documents.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.documents.api.exception.GlobalException;

class OutboundAuthHeaderFactoryTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("성공_USER_DELEGATION 모드는 Authorization과 사용자 컨텍스트 헤더를 전달한다")
    void createHeadersForUserDelegation() {
        ServiceTokenProperties serviceTokenProperties = new ServiceTokenProperties();
        OutboundAuthHeaderFactory factory = new OutboundAuthHeaderFactory(serviceTokenProperties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer user-access-token");
        request.addHeader(GatewayAuthContext.USER_ID_HEADER, "user-123");
        request.addHeader(GatewayAuthContext.USER_ROLE_HEADER, "editor");
        request.addHeader(GatewayAuthContext.REQUEST_ID_HEADER, "req-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var headers = factory.createHeaders(OutboundAuthMode.USER_DELEGATION);

        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer user-access-token");
        assertThat(headers.getFirst(GatewayAuthContext.USER_ID_HEADER)).isEqualTo("user-123");
        assertThat(headers.getFirst(GatewayAuthContext.USER_ROLE_HEADER)).isEqualTo("editor");
        assertThat(headers.getFirst(GatewayAuthContext.REQUEST_ID_HEADER)).isEqualTo("req-1");
    }

    @Test
    @DisplayName("실패_USER_DELEGATION 모드에서 Bearer 토큰이 없으면 인증 오류를 반환한다")
    void createHeadersForUserDelegationFailsWithoutBearerToken() {
        ServiceTokenProperties serviceTokenProperties = new ServiceTokenProperties();
        OutboundAuthHeaderFactory factory = new OutboundAuthHeaderFactory(serviceTokenProperties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic abc123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThatThrownBy(() -> factory.createHeaders(OutboundAuthMode.USER_DELEGATION))
            .isInstanceOf(GlobalException.class);
    }

    @Test
    @DisplayName("성공_SERVICE_TO_SERVICE 모드는 서비스 토큰과 요청 ID를 전달한다")
    void createHeadersForServiceToService() {
        ServiceTokenProperties serviceTokenProperties = new ServiceTokenProperties();
        serviceTokenProperties.setBearerToken("internal-service-token");
        OutboundAuthHeaderFactory factory = new OutboundAuthHeaderFactory(serviceTokenProperties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(GatewayAuthContext.REQUEST_ID_HEADER, "req-2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var headers = factory.createHeaders(OutboundAuthMode.SERVICE_TO_SERVICE);

        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer internal-service-token");
        assertThat(headers.getFirst(GatewayAuthContext.REQUEST_ID_HEADER)).isEqualTo("req-2");
    }

    @Test
    @DisplayName("실패_SERVICE_TO_SERVICE 모드에서 서비스 토큰이 없으면 예외가 발생한다")
    void createHeadersForServiceToServiceFailsWithoutToken() {
        ServiceTokenProperties serviceTokenProperties = new ServiceTokenProperties();
        OutboundAuthHeaderFactory factory = new OutboundAuthHeaderFactory(serviceTokenProperties);

        assertThatThrownBy(() -> factory.createHeaders(OutboundAuthMode.SERVICE_TO_SERVICE))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("내부 서비스 토큰이 설정되지 않았습니다.");
    }
}
