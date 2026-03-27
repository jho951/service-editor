package com.documents.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BusinessErrorCode {
    VALIDATION_ERROR("요청 필드 유효성 검사에 실패했습니다."),
    INVALID_REQUEST("잘못된 요청입니다."),
    CONFLICT("요청이 현재 리소스 상태와 충돌합니다."),
    SORT_KEY_REBALANCE_REQUIRED("정렬 키 공간이 부족하여 재정렬이 필요합니다."),
    DOCUMENT_NOT_FOUND("요청한 문서를 찾을 수 없습니다."),
    BLOCK_NOT_FOUND("요청한 블록을 찾을 수 없습니다.");

    private final String message;
}
