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
 * 模型配置实体。
 * <p>
 * 把模型配置放进数据库而不是 application.yml，是为了支持「改配置不重启」：
 * 后台改完立即生效，这对演示和答辩场景很关键。
 * <p>
 * {@code apiKey} 必须加密存储（AES），禁止明文入库，也禁止在接口响应中回显完整值。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
@TableName("ai_model_config")
public class AiModelConfigDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提供方：openai / dashscope / deepseek / ollama，仅作标识 */
    private String provider;

    /** 模型名，如 gpt-4o-mini、deepseek-chat、text-embedding-3-small */
    private String modelName;

    /** 类型：chat / embedding */
    private String modelType;

    /** 接口基址，支持任意 OpenAI 兼容端点，换供应商只改这里 */
    private String baseUrl;

    /** 加密后的 API Key */
    private String apiKey;

    /** 输入价格（每 1K token，元），用于成本核算，可为空 */
    private BigDecimal inputPrice;

    /** 输出价格（每 1K token，元），用于成本核算，可为空 */
    private BigDecimal outputPrice;

    /** 是否默认：1 是，0 否。同类型下只允许一个默认 */
    private Integer isDefault;

    /** 状态：1 启用，0 停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime gmtCreate;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime gmtModified;
}
