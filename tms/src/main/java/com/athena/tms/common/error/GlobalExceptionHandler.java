package com.athena.tms.common.error;

import com.athena.tms.common.error.ApiError;
import com.athena.tms.common.error.ApiException;
import com.athena.tms.common.codes.ErrorCode;
import com.athena.tms.filter.IdempotencyFilter;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(value={ApiException.class})
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        return ResponseEntity.status((int)ex.getStatus()).body(new ApiError(ex.getCode(), ex.getMessage(), null, ex.getContext()));
    }

    @ExceptionHandler(value={MissingRequestHeaderException.class})
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        if (IdempotencyFilter.IDEMPOTENCY_KEY_HEADER.equalsIgnoreCase(ex.getHeaderName())) {
            return ResponseEntity.badRequest().body(new ApiError(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "這個操作必須帶 Idempotency-Key", null, null));
        }
        return ResponseEntity.badRequest().body(new ApiError("validation_failed", "缺少必帶的標頭 " + ex.getHeaderName(), null, null));
    }

    @ExceptionHandler(value={MethodArgumentNotValidException.class})
    public ResponseEntity<ApiError> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<ApiError.Detail> details = ex.getBindingResult().getFieldErrors().stream().map(fieldError -> new ApiError.Detail(fieldError.getField(), fieldError.getDefaultMessage())).toList();
        return ResponseEntity.unprocessableEntity().body(new ApiError("validation_failed", "請求內容驗證失敗", details, null));
    }
}
