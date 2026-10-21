package com.zhanglang.kb.rag.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建知识库请求。
 * <p>
 * 检索参数允许建库时指定，未指定则由数据库默认值兜底。
 * 这些参数的取值区间经过实测，超出区间不是"效果差一点"而是"完全不可用"：
 * chunkSize 过小会把一句话切成两半，topK 过大会把无关内容塞进 Prompt 反而降低准确率。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
public class KbCreateRequest {

    /** 知识库名称 */
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 128, message = "名称长度不能超过 128 位")
    private String name;

    /** 描述 */
    @Size(max = 512, message = "描述长度不能超过 512 位")
    private String description;

    /** 切分块大小（字符），建议 400-1000 */
    @Min(value = 200, message = "切分块大小不能小于 200")
    @Max(value = 2000, message = "切分块大小不能大于 2000")
    private Integer chunkSize;

    /** 相邻块重叠字符数，必须小于 chunkSize，建议为 chunkSize 的 10%-15% */
    @Min(value = 0, message = "重叠字符数不能为负")
    @Max(value = 500, message = "重叠字符数不能大于 500")
    private Integer chunkOverlap;

    /** 召回条数，建议 3-8 */
    @Min(value = 1, message = "召回条数不能小于 1")
    @Max(value = 20, message = "召回条数不能大于 20")
    private Integer topK;

    /** 相似度阈值，0-1 之间，越高越严格 */
    @DecimalMin(value = "0.0", message = "相似度阈值不能小于 0")
    @DecimalMax(value = "1.0", message = "相似度阈值不能大于 1")
    private BigDecimal similarityThreshold;
}
