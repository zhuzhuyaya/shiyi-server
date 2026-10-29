package com.shiyi.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? "参数错误" : fe.getDefaultMessage();
        return Result.fail(400, msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "系统繁忙，请稍后重试");
    }

    /** 未登录：401 语义 */
    @ExceptionHandler(cn.dev33.satoken.exception.NotLoginException.class)
    public Result<Void> handleNotLogin(cn.dev33.satoken.exception.NotLoginException e) {
        log.debug("未登录访问: {}", e.getMessage());
        return Result.fail(401, "未登录或登录已过期");
    }

    /** 无权限：403 语义 */
    @ExceptionHandler(cn.dev33.satoken.exception.NotRoleException.class)
    public Result<Void> handleNotRole(cn.dev33.satoken.exception.NotRoleException e) {
        return Result.fail(403, "无权访问");
    }
}
