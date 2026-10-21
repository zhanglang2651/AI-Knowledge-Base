package com.zhanglang.kb.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体。
 * <p>
 * 所有对前端暴露的接口都必须返回该结构，不允许直接返回裸对象或裸集合。
 * 这样前端只需要写一套响应拦截逻辑，也方便后续统一加签名、加密等处理。
 *
 * @param <T> 业务数据类型
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务错误码，00000 表示成功 */
    private String code;

    /** 提示信息，可直接展示给用户 */
    private String message;

    /** 业务数据，失败时为 null */
    private T data;

    /**
     * 服务端生成响应时的毫秒时间戳，便于前端排查时序问题。
     * 不使用 java.util.Date，遵循手册中「禁止使用 Date 作为传输对象字段」的建议。
     */
    private long timestamp;

    private Result(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 构造成功响应（无数据）。
     *
     * @param <T> 业务数据类型
     * @return 成功响应体
     */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 构造成功响应（带数据）。
     *
     * @param data 业务数据
     * @param <T>  业务数据类型
     * @return 成功响应体
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 构造失败响应。
     *
     * @param resultCode 错误码枚举
     * @param <T>        业务数据类型
     * @return 失败响应体
     */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /**
     * 构造失败响应，允许覆盖默认提示语。
     * 仅在前端需要更具体的引导文案时使用，不要用它来暴露异常堆栈或 SQL 信息。
     *
     * @param resultCode 错误码枚举
     * @param message    自定义提示信息
     * @param <T>        业务数据类型
     * @return 失败响应体
     */
    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    /**
     * 构造失败响应（裸错误码）。
     * <p>
     * 供 {@code GlobalExceptionHandler} 还原业务异常自带的错误码时使用。
     * 业务代码应优先使用枚举重载，避免魔法字符串散落各处。
     *
     * @param code    5 位错误码
     * @param message 提示信息
     * @param <T>     业务数据类型
     * @return 失败响应体
     */
    public static <T> Result<T> fail(String code, String message) {
        return new Result<>(code, message, null);
    }
}
