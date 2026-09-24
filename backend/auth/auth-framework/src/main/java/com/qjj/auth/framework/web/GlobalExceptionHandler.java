package com.qjj.auth.framework.web;

import com.qjj.auth.common.enums.SysErrorCodeEnum;
import com.qjj.auth.common.exception.BizException;
import com.qjj.auth.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException ex) {
        if (ex.getCode() == SysErrorCodeEnum.RATE_LIMITED.getCode()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Result.fail(ex.getCode(), ex.getMessage()));
        }
        if (ex.getCode() == SysErrorCodeEnum.UNAUTHENTICATED.getCode()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.fail(ex.getCode(), ex.getMessage()));
        }
        if (ex.getCode() == SysErrorCodeEnum.FORBIDDEN.getCode()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.fail(ex.getCode(), ex.getMessage()));
        }
        return ResponseEntity.ok(Result.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage() == null ? "非法" : fe.getDefaultMessage());
        }
        return ResponseEntity.ok(Result.fail(SysErrorCodeEnum.VALIDATION_FAILED.getCode(),
                SysErrorCodeEnum.VALIDATION_FAILED.getDesc(), fields));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.fail(SysErrorCodeEnum.FORBIDDEN));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception ex) {
        log.error("系统异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(SysErrorCodeEnum.SYSTEM_ERROR));
    }
}
