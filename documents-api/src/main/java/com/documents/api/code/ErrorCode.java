package com.documents.api.code;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, false, 9015, "잘못된 요청입니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, false, 9016, "요청 필드 유효성 검사에 실패했습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, false, 9017, "허용되지 않은 HTTP 메서드입니다."),
    NOT_FOUND_URL(HttpStatus.NOT_FOUND, false, 9002, "요청하신 URL을 찾을 수 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, false, 9003, "요청한 리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, false, 9004, "요청이 현재 리소스 상태와 충돌합니다."),
    FAIL(HttpStatus.BAD_REQUEST, false, 9999, "요청 응답 실패, 관리자에게 문의해주세요.");

    private final HttpStatus httpStatus;
    private final boolean success;
    private final int code;
    private final String message;
}
