package com.zhanglang.kb.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型配置展示对象。
 * <p>
 * {@code apiKey} 字段只返回脱敏值（如 {@code sk-H****h1Ht}），
 * 完整密钥永远不出后端 —— 这是密钥管理的底线，不是"可选优化"。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
@Builder
public class ModelConfigVO {

    private Long id;

    private String provider;

    private String modelName;

    /** chat / embedding */
    private String modelType;

    private String baseUrl;

    /** 脱敏后的 API Key，仅用于确认"已配置" */
    private String apiKeyMasked;

    private BigDecimal inputPrice;

    private BigDecimal outputPrice;

    private Integer isDefault;

    private Integer status;

    private LocalDateTime gmtCreate;
}
