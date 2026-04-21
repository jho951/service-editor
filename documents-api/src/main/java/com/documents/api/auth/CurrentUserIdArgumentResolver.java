package com.documents.api.auth;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.documents.api.code.ErrorCode;
import com.documents.api.exception.GlobalException;

@Component
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
            && String.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        Object userId = webRequest.getAttribute(
            GatewayAuthContext.REQUEST_USER_ID_ATTRIBUTE,
            NativeWebRequest.SCOPE_REQUEST
        );

        if (userId instanceof String authenticatedUserId) {
            return authenticatedUserId;
        }

        if (webRequest.getUserPrincipal() != null && StringUtils.hasText(webRequest.getUserPrincipal().getName())) {
            return webRequest.getUserPrincipal().getName().trim();
        }

        String headerUserId = webRequest.getHeader(GatewayAuthContext.USER_ID_HEADER);
        if (!StringUtils.hasText(headerUserId)) {
            throw new GlobalException(ErrorCode.UNAUTHORIZED);
        }

        return headerUserId.trim();
    }
}
