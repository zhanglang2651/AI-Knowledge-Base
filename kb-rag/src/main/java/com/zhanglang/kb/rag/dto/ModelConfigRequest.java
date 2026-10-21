package com.zhanglang.kb.rag.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模型配置请求（新增 / 更新共用）。
 * <p>
 * {@code apiKey} 允许为空：更新时留空表示「不修改原密钥」，
 * 这样前端不需要每次编辑都要求用户重新粘贴 key ——
 * 前端本来就拿不到完整 key（只回显脱敏值），强制重填的体验很差。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
public class ModelConfigRequest {

    /** 提供方标识：openai / dashscope / deepseek / ollama 等，仅作分类展示 */
    @NotBlank(message = "提供方不能为空")
    @Size(max = 32, message = "提供方长度不能超过 32 位")
    private String provider;

    /** 模型名，如 agnes-3.0-flash、BAAI/bge-m3 */
    @NotBlank(message = "模型名不能为空")
    @Size(max = 64, message = "模型名长度不能超过 64 位")
    private String modelName;

    /** 类型：chat / embedding */
    @NotBlank(message = "模型类型不能为空")
    @Pattern(regexp = "^(chat|embedding)$", message = "模型类型只能是 chat 或 embedding")
    private String modelType;

    /** 接口基址，必须带 /v1 */
    @NotBlank(message = "baseUrl 不能为空")
    @Size(max = 255, message = "baseUrl 长度不能超过 255 位")
    private String baseUrl;

    /** API Key。更新时留空表示不修改 */
    @Size(max = 512, message = "API Key 长度不能超过 512 位")
    private String apiKey;

    /** 输入单价（每 1K token，元），用于成本核算 */
    @DecimalMin(value = "0", message = "输入单价不能为负")
    private BigDecimal inputPrice;

    /** 输出单价（每 1K token，元） */
    @DecimalMin(value = "0", message = "输出单价不能为负")
    private BigDecimal outputPrice;

    /** 是否设为该类型下的默认模型：1 是，0 否 */
    private Integer isDefault;

    /** 状态：1 启用，0 停用 */
    private Integer status;
}
