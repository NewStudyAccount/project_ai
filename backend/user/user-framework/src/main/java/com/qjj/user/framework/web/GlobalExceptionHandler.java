package com.qjj.user.framework.web;

import com.qjj.user.common.enums.SysErrorCodeEnum;
import com.qjj.user.common.exception.BizException;
import com.qjj.user.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理（CLAUDE.md 6.4.3 / 5.3）：
 * 未认证 401·10001；无权限 403·10002；限流 429·10003；业务/校验失败 HTTP 200 由 code 表达；系统异常 5xx·10004 不泄堆栈。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException ex) {
        return writeBiz(ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage() == null ? "非法" : fe.getDefaultMessage());
        }
        return ResponseEntity.ok(Result.fail(
                SysErrorCodeEnum.VALIDATION_FAILED.getCode(), SysErrorCodeEnum.VALIDATION_FAILED.getDesc(), fields));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception ex) {
        BizException biz = findBiz(ex);
        if (biz != null) {
            return writeBiz(biz);
        }
        log.error("系统异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(SysErrorCodeEnum.SYSTEM_ERROR));
    }

    private ResponseEntity<Result<Void>> writeBiz(BizException ex) {
        if (ex.getCode() == SysErrorCodeEnum.RATE_LIMITED.getCode()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Result.fail(ex.getCode(), ex.getMessage()));
        }
        if (ex.getCode() == SysErrorCodeEnum.UNAUTHENTICATED.getCode()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.fail(ex.getCode(), ex.getMessage()));
        }
        if (ex.getCode() == SysErrorCodeEnum.FORBIDDEN.getCode()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.fail(ex.getCode(), ex.getMessage()));
        }
        if (ex.getCode() == SysErrorCodeEnum.SYSTEM_ERROR.getCode()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.fail(ex.getCode(), ex.getMessage()));
        }
        // 业务失败 2xxxxx / 校验失败 3xxxx：HTTP 200，语义由 code 表达
        return ResponseEntity.ok(Result.fail(ex.getCode(), ex.getMessage()));
    }

    private BizException findBiz(Throwable ex) {
        Throwable cur = ex;
        int depth = 0;
        while (cur != null && depth++ < 8) {
            if (cur instanceof BizException biz) {
                return biz;
            }
            cur = cur.getCause() == cur ? null : cur.getCause();
        }
        return null;
    }
}
