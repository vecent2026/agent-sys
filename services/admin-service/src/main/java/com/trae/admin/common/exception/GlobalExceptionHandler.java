package com.trae.admin.common.exception;

import com.trae.admin.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.error("Business Exception: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.error("Validation Exception: {}", message);
        return Result.error(400, message);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.error("Bind Exception: {}", message);
        return Result.error(400, message);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDeniedException(AccessDeniedException e) {
        log.error("Access Denied: {}", e.getMessage());
        return Result.error(403, "权限不足");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public Result<Void> handleBadCredentialsException(BadCredentialsException e) {
        log.error("Bad Credentials: {}", e.getMessage());
        return Result.error(401, "账号或密码错误");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("System Exception", e);
        return Result.error(500, "系统内部错误");
    }
}
