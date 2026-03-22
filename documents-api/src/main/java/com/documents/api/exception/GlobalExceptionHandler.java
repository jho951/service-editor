package com.documents.api.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.documents.api.code.ErrorCode;
import com.documents.api.dto.GlobalResponse;
import com.documents.exception.BusinessException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GlobalException.class)
    public ResponseEntity<GlobalResponse<Void>> handleGlobalException(GlobalException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(GlobalResponse.fail(errorCode));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<GlobalResponse<Void>> handleBusinessException(BusinessException ex) {
      ErrorCode errorCode = mapBusinessError(ex.getErrorCode().name());
      return ResponseEntity.status(errorCode.getHttpStatus())
        .body(GlobalResponse.fail(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GlobalResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(GlobalResponse.fail(ErrorCode.VALIDATION_ERROR));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<GlobalResponse<Void>> handleMissingRequestHeaderException(MissingRequestHeaderException ex) {
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getHttpStatus())
                .body(GlobalResponse.fail(ErrorCode.UNAUTHORIZED));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<GlobalResponse<Void>> handleMissingRequestParameterException(
            MissingServletRequestParameterException ex
    ) {
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(GlobalResponse.fail(ErrorCode.VALIDATION_ERROR));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<GlobalResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getHttpStatus())
                .body(GlobalResponse.fail(ErrorCode.INVALID_REQUEST));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GlobalResponse<Void>> handleException(Exception ex) {
        return ResponseEntity.status(ErrorCode.FAIL.getHttpStatus())
                .body(GlobalResponse.fail(ErrorCode.FAIL));
    }

    private ErrorCode mapBusinessError(String errorCodeName) {
        try {
            return ErrorCode.valueOf(errorCodeName);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("No API error mapping for business error: " + errorCodeName, ex);
        }
    }
}
