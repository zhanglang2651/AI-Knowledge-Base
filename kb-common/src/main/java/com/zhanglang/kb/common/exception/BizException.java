package com.zhanglang.kb.common.exception;

import com.zhanglang.kb.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常。
 * <p>
 * 所有「可预期」的错误（参数不合法、资源不存在、无权访问）都应抛出该异常，
 * 由 {@link GlobalExceptionHandler} 统一转换为标准响应体。
 * <p>
 * 禁止用它包裹系统级异常：<b>业务异常不打错误堆栈</b>，
 * 若把 NPE、SQL 异常也塞进来，会导致线上真实故障在日志里丢失堆栈信息。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务错误码，对应 {@link ResultCode} */
    private final String code;

    /**
     * 使用预定义错误码构造异常。
     *
     * @param resultCode 错误码枚举
     */
    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * 使用预定义错误码构造异常，并覆盖提示信息。
     *
     * @param resultCode 错误码枚举
     * @param message    自定义提示信息
     */
    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    /**
     * 使用预定义错误码构造异常，保留底层异常作为 cause。
     * <p>
     * 注意：这仅用于需要保留调用链的场景（如包装第三方 SDK 异常），
     * 此时应同时记录日志，否则 cause 的堆栈不会被输出。
     *
     * @param resultCode 错误码枚举
     * @param message    自定义提示信息
     * @param cause      原始异常
     */
    public BizException(ResultCode resultCode, String message, Throwable cause) {
        super(message, cause);
        this.code = resultCode.getCode();
    }
}
