/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.http.ResponseEntity
 *  org.springframework.web.bind.MethodArgumentNotValidException
 *  org.springframework.web.bind.annotation.ExceptionHandler
 *  org.springframework.web.bind.annotation.RestControllerAdvice
 */
package com.athena.tms.common.error;

import com.athena.tms.common.error.ApiError;
import com.athena.tms.common.error.ApiException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(value={ApiException.class})
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        return ResponseEntity.status((int)ex.getStatus()).body(ApiError.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(value={MethodArgumentNotValidException.class})
    public ResponseEntity<ApiError> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<ApiError.Detail> details = ex.getBindingResult().getFieldErrors().stream().map(fieldError -> new ApiError.Detail(fieldError.getField(), fieldError.getDefaultMessage())).toList();
        return ResponseEntity.unprocessableEntity().body(new ApiError("validation_failed", "\u8acb\u6c42\u5167\u5bb9\u9a57\u8b49\u5931\u6557", details));
    }
}
