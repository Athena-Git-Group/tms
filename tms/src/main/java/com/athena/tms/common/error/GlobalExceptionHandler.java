package com.athena.tms.common.error;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * 全域例外→ApiError 映射（409/404/401/403/422/429）。
 * 業務碼只丟 ApiException 子類，不各自組回應——錯誤格式只在這一處定義，
 * 回應形狀才不會隨 controller 數量漂移。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getCode(), ex.getMessage()));
    }

    /** Bean Validation（@Valid）失敗統一回 422，欄位明細進 details。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<ApiError.Detail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ApiError.Detail(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return ResponseEntity.unprocessableEntity()
                .body(new ApiError(UnprocessableEntityException.DEFAULT_CODE, "請求內容驗證失敗", details));
    }
}
