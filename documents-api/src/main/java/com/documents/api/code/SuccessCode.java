package com.documents.api.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SuccessCode {
    SUCCESS(HttpStatus.OK, true, 200, "요청 응답 성공"),
    CREATED(HttpStatus.CREATED, true, 201, "리소스 생성 성공");

    private final HttpStatus httpStatus;
    private final boolean success;
    private final int code;
    private final String message;
}
