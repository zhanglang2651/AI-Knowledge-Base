package com.zhanglang.kb.common.result;

import lombok.Getter;

/**
 * 统一错误码枚举。
 * <p>
 * 编码规则依据《阿里巴巴 Java 开发手册》错误码规约：
 * 共 5 位，由「错误来源 + 4 位数字编号」组成，不体现版本号和错误等级。
 * <ul>
 *     <li>A：错误来源于用户，如参数错误、用户不存在、登录超时</li>
 *     <li>B：错误来源于当前系统，如业务逻辑出错、资源不存在</li>
 *     <li>C：错误来源于第三方服务，如模型 API 超时、限流</li>
 * </ul>
 * 正常响应的错误码固定为 00000，这是手册的强制约定，不要改成 200。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Getter
public enum ResultCode {

    /** 成功。手册约定：全部正常但不得不填充错误码时，返回五个零 */
    SUCCESS("00000", "成功"),

    /* ============ A 类：用户侧错误 ============ */

    /** 请求参数不合法，通常由参数校验失败触发 */
    PARAM_INVALID("A0400", "请求参数不合法"),

    /** 用户名已存在 */
    USERNAME_EXISTS("A0201", "用户名已存在"),

    /** 账号或密码错误。此处不区分"用户不存在"与"密码错误"，避免账号枚举攻击 */
    LOGIN_FAILED("A0202", "账号或密码错误"),

    /** 未登录或 Token 已过期 */
    UNAUTHORIZED("A0301", "未登录或登录已过期"),

    /** 已登录但无权访问目标资源 */
    FORBIDDEN("A0302", "无权访问该资源"),

    /* ============ B 类：系统侧错误 ============ */

    /** 兜底系统异常 */
    SYSTEM_ERROR("B0001", "系统内部错误，请稍后重试"),

    /** 知识库不存在或已删除 */
    KB_NOT_FOUND("B0101", "知识库不存在"),

    /** 文档不存在或已删除 */
    DOC_NOT_FOUND("B0102", "文档不存在"),

    /** 文档解析失败，具体原因见日志 */
    DOC_PARSE_FAILED("B0103", "文档解析失败"),

    /** 上传文件类型不在白名单内 */
    FILE_TYPE_UNSUPPORTED("B0104", "不支持的文件类型"),

    /** 上传文件超过大小限制 */
    FILE_TOO_LARGE("B0105", "文件大小超出限制"),

    /** 会话不存在 */
    CONVERSATION_NOT_FOUND("B0106", "会话不存在"),

    /* ============ C 类：第三方服务错误 ============ */

    /** 模型服务返回异常 */
    AI_CALL_FAILED("C0001", "模型服务调用失败"),

    /** 模型服务超时 */
    AI_TIMEOUT("C0002", "模型服务响应超时");

    /** 5 位错误码，字符串类型（手册强制：错误码必须为字符串，防止前导零丢失） */
    private final String code;

    /** 面向用户的提示信息，不要在这里暴露内部实现细节 */
    private final String message;

    ResultCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
