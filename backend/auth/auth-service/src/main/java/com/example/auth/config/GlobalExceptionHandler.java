package com.example.auth.config;

import com.example.auth.common.BizException;
import com.example.auth.common.ErrorCode;
import com.example.auth.common.Result;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理；禁止把堆栈返回前端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException ex) {
        HttpStatus status = ex.getCode() == ErrorCode.USER_CENTER_UNAVAILABLE.code()
                || ex.getCode() == ErrorCode.LOGIN_LOCKED.code()
                ? HttpStatus.SERVICE_UNAVAILABLE
                : (ex.getCode() == ErrorCode.CREDENTIAL_INVALID.code()
                    || ex.getCode() == ErrorCode.ACCOUNT_DISABLED.code()
                    || ex.getCode() == ErrorCode.UNAUTHORIZED.code()
                    ? HttpStatus.UNAUTHORIZED : HttpStatus.OK);
        return ResponseEntity.status(status).body(Result.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Result.fail(ErrorCode.VALIDATION_ERROR.code(), msg.isBlank() ? ErrorCode.VALIDATION_ERROR.msg() : msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception ex) {
        log.error("system error", ex);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }
}
