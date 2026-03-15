package com.documents.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BusinessErrorCode {
    RESOURCE_NOT_FOUND("요청한 리소스를 찾을 수 없습니다.");

    private final String message;
}
