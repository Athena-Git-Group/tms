package com.athena.tms.common.error;

import com.athena.tms.common.error.ApiError;
import com.athena.tms.common.error.ApiException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(value={ApiException.class})
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        return ResponseEntity.status((int)ex.getStatus()).body(new ApiError(ex.getCode(), ex.getMessage(), null, ex.getContext()));
    }

    @ExceptionHandler(value={MissingRequestHeaderException.class})
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.badRequest().body(new ApiError("validation_failed", "缺少必帶的標頭 " + ex.getHeaderName(), null, null));
    }

    /** 查詢參數轉不成宣告的型別（例如 assigneeId 不是 UUID、from 不是 ISO 日期）時，回 400 並用與其他錯誤相同的 Error 形狀。 */
    @ExceptionHandler(value={MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(new ApiError("validation_failed", "參數 " + ex.getName() + " 的格式不正確", null, null));
    }

    @ExceptionHandler(value={MethodArgumentNotValidException.class})
    public ResponseEntity<ApiError> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<ApiError.Detail> details = ex.getBindingResult().getFieldErrors().stream().map(fieldError -> new ApiError.Detail(fieldError.getField(), fieldError.getDefaultMessage())).toList();
        return ResponseEntity.unprocessableEntity().body(new ApiError("validation_failed", "請求內容驗證失敗", details, null));
    }
}
