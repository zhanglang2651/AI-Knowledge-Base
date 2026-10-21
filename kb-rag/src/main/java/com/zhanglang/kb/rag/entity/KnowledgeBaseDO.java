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
 * 知识库实体。
 * <p>
 * 每个知识库持有<b>独立的检索参数</b>（切分粒度、召回条数、相似度阈值），
 * 这是「多知识库」区别于「一个大文档目录」的核心：不同领域资料的理想切分粒度本来就不同。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
@TableName("kb_knowledge_base")
public class KnowledgeBaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识库名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 归属用户 ID，用于数据隔离，不可为空 */
    private Long ownerId;

    /** 向量模型标识，仅用于前端展示与排查，实际模型由配置决定 */
    private String embeddingModel;

    /**
     * 向量维度。
     * <p>
     * <b>这是硬约束</b>：pgvector 的 vector(n) 在建表时确定，换模型必须重建向量表。
     * 因此单独落库，启动时可与当前模型的实际维度比对，不一致直接失败，
     * 避免运行到一半才报维度不匹配（这类错误极难定位）。
     */
    private Integer embeddingDim;

    /** 切分块大小（字符数） */
    private Integer chunkSize;

    /** 相邻块重叠字符数，用于缓解语义被切断 */
    private Integer chunkOverlap;

    /** 检索召回条数 */
    private Integer topK;

    /** 相似度阈值，低于该值的片段视为不相关 */
    private BigDecimal similarityThreshold;

    /** 已完成的文档数，冗余字段，避免列表页做 count 查询 */
    private Integer docCount;

    /** 状态：1 启用，0 停用 */
    private Integer status;

    /** 逻辑删除标记 */
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime gmtCreate;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime gmtModified;
}
