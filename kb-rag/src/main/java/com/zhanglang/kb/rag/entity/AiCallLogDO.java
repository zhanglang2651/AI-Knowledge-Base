package com.zhanglang.kb.rag.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 调用日志实体。
 * <p>
 * 每次调用大模型或向量模型写一条，用于成本核算与故障排查。
 * 注意：写入应为<b>异步且不影响主链路</b>，日志写失败绝不能导致用户问答失败。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
@TableName("ai_call_log")
public class AiCallLogDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 调用者用户 ID */
    private Long userId;

    /** 调用场景：CHAT / EMBEDDING */
    private String scene;

    /** 实际使用的模型名 */
    private String model;

    /** 输入 token 数 */
    private Integer promptTokens;

    /** 输出 token 数 */
    private Integer completionTokens;

    /** 本次调用费用（元），按配置单价计算，单价缺失时为 0 */
    private BigDecimal cost;

    /** 耗时（毫秒） */
    private Integer latencyMs;

    /** 是否成功：1 成功，0 失败 */
    private Integer isSuccess;

    /** 失败时的错误码 */
    private String errorCode;

    /** 链路追踪 ID，与日志 MDC 中的 traceId 一致，便于串联排查 */
    private String traceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime gmtCreate;
}
