package com.documents.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BusinessErrorCode {
    INVALID_REQUEST("잘못된 요청입니다."),
    SORT_KEY_REBALANCE_REQUIRED("정렬 키 공간이 부족하여 재정렬이 필요합니다."),
    WORKSPACE_NOT_FOUND("요청한 워크스페이스를 찾을 수 없습니다."),
    DOCUMENT_NOT_FOUND("요청한 문서를 찾을 수 없습니다."),
    BLOCK_NOT_FOUND("요청한 블록을 찾을 수 없습니다.");

    private final String message;
}
