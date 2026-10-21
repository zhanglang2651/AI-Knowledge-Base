package com.zhanglang.kb.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知识库展示对象。
 * <p>
 * 与 {@code KnowledgeBaseDO} 分离，避免把 ownerId 以外的内部字段（逻辑删除标记等）暴露给前端，
 * 也便于后续调整展示结构而不影响数据库模型。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
@Builder
public class KbVO {

    private Long id;

    private String name;

    private String description;

    /** 已完成的文档数 */
    private Integer docCount;

    /** 向量模型标识 */
    private String embeddingModel;

    /** 向量维度 */
    private Integer embeddingDim;

    private Integer chunkSize;

    private Integer chunkOverlap;

    private Integer topK;

    private BigDecimal similarityThreshold;

    /** 1 启用，0 停用 */
    private Integer status;

    private LocalDateTime gmtCreate;
}
