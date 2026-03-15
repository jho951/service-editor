package com.documents.api.dto;

import com.documents.api.code.ErrorCode;
import com.documents.api.code.SuccessCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@Schema(description = "공통 응답 구조")
public final class GlobalResponse<T> {

    @Schema(description = "HTTP 상태 코드", example = "200")
    private final HttpStatus httpStatus;

    @Schema(description = "성공 여부", example = "true")
    private final boolean success;

    @Schema(description = "응답 메시지", example = "요청이 성공적으로 처리되었습니다.")
    private final String message;

    @Schema(description = "비즈니스 코드", example = "1000")
    private final int code;

    @Schema(description = "응답 데이터")
    private final T data;

    public GlobalResponse(HttpStatus httpStatus, boolean success, String message, int code, T data) {
        if (httpStatus == null || message == null) {
            throw new IllegalArgumentException("HTTP 상태와 메시지는 null일 수 없습니다.");
        }
        this.httpStatus = httpStatus;
        this.success = success;
        this.message = message;
        this.code = code;
        this.data = data;
    }

    public static <T> GlobalResponse<T> ok(SuccessCode successCode, T data) {
        if (successCode == null) {
            throw new IllegalArgumentException("성공 코드는 null일 수 없습니다.");
        }
        return new GlobalResponse<>(
                successCode.getHttpStatus(),
                successCode.isSuccess(),
                successCode.getMessage(),
                successCode.getCode(),
                data
        );
    }

    public static GlobalResponse<Void> ok() {
        return ok(SuccessCode.SUCCESS, null);
    }

    public static GlobalResponse<Void> fail(ErrorCode errorCode) {
        if (errorCode == null) {
            throw new IllegalArgumentException("에러 코드는 null일 수 없습니다.");
        }
        return new GlobalResponse<>(
                errorCode.getHttpStatus(),
                errorCode.isSuccess(),
                errorCode.getMessage(),
                errorCode.getCode(),
                null
        );
    }
}
