package com.ai.pub.exception;

import com.ai.pub.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 权限不足。
     *
     * 必须单独声明：AccessDeniedException 是 RuntimeException 的子类，
     * 若只有 RuntimeException 处理器，方法级 @PreAuthorize("hasRole('ADMIN')")
     * 拒绝时会返回 code=500 与原始异常消息，前端提示"请求失败"而不是"无权限"。
     *
     * 返回 HTTP 403 + code=403，与 SecurityConfig 中 AccessDeniedHandler
     * 处理过滤器链层拒绝时的行为保持一致。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.error(403, "无权限访问该资源"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<Void>> handleRuntimeException(RuntimeException e) {
        // 业务异常（如"用户不存在""AppId已存在"）保持 HTTP 200 + code=500 的既有约定，
        // 便于前端统一按响应体 code 判断
        log.warn("业务异常: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.OK).body(Result.error(500, e.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthenticationException(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.OK).body(Result.error(401, "认证失败"));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Result<Void>> handleBadCredentialsException(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.OK).body(Result.error(401, "用户名或密码错误"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        return ResponseEntity.status(HttpStatus.OK).body(Result.error(500, e.getMessage()));
    }
}