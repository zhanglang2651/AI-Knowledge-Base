package com.zhanglang.kb.common.exception;

import com.zhanglang.kb.common.result.Result;
import com.zhanglang.kb.common.result.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * <p>
 * 设计原则：
 * <ol>
 *     <li>业务异常（{@link BizException}）只记 WARN，不打堆栈 —— 它是预期内的分支，打堆栈会淹没真正的问题</li>
 *     <li>系统异常记 ERROR 并打完整堆栈 —— 这是排查线上问题的唯一线索</li>
 *     <li>返回给前端的 message 一律使用预置文案，绝不回传异常原文（防止泄露表名、SQL、内网地址）</li>
 * </ol>
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     *
     * @param ex 业务异常
     * @return 标准失败响应
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException ex) {
        // 业务异常属于预期分支，记 WARN 即可，不打堆栈
        log.warn("业务异常, code={}, message={}", ex.getCode(), ex.getMessage());
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    /**
     * 处理 @RequestBody 参数校验失败。
     * 将第一条字段错误返回给前端，便于用户立即修正，而不是笼统提示"参数错误"。
     *
     * @param ex 校验异常
     * @return 标准失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败, detail={}", detail);
        return Result.fail(ResultCode.PARAM_INVALID, detail);
    }

    /**
     * 处理表单/Query 参数绑定校验失败。
     *
     * @param ex 绑定异常
     * @return 标准失败响应
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException ex) {
        String detail = ex.getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败, detail={}", detail);
        return Result.fail(ResultCode.PARAM_INVALID, detail);
    }

    /**
     * 处理方法级参数校验失败（@Validated 加在类上时触发）。
     *
     * @param ex 约束违反异常
     * @return 标准失败响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("约束校验失败, detail={}", detail);
        return Result.fail(ResultCode.PARAM_INVALID, detail);
    }

    /**
     * 处理缺少必填请求参数。
     *
     * @param ex 缺参异常
     * @return 标准失败响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("缺少必填参数, param={}", ex.getParameterName());
        return Result.fail(ResultCode.PARAM_INVALID, "缺少必填参数：" + ex.getParameterName());
    }

    /**
     * 处理上传文件超限。
     * 该异常由 Spring 在解析 multipart 阶段抛出，此时业务代码尚未执行，
     * 因此必须在这里单独捕获，否则用户只会看到一个 500。
     *
     * @param ex 超限异常
     * @return 标准失败响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("上传文件超出大小限制, message={}", ex.getMessage());
        return Result.fail(ResultCode.FILE_TOO_LARGE);
    }

    /**
     * 兜底异常处理。
     * 任何未被上面分支捕获的异常都会落到这里，必须打印完整堆栈。
     *
     * @param ex 未知异常
     * @return 标准失败响应
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        // 这里必须打堆栈：这是排查线上故障的唯一入口
        log.error("系统异常", ex);
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }

    /**
     * 将字段错误格式化为「字段名: 提示」。
     */
    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
