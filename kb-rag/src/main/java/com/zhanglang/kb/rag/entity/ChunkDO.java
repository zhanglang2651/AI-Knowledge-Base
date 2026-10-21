package com.zhanglang.kb.rag.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分块正文实体。
 * <p>
 * 向量库只负责"语义相近"，关键词精确命中（如产品型号、错误码、英文缩写）
 * 恰恰是它的短板 —— "ERR-1042" 和 "ERR-1043" 在 embedding 空间里几乎同质。
 * 因此入库时把分块正文另外落一份到本表，供关键词分支召回。
 * <p>
 * <b>本表与向量表是一一对应的</b>：删除文档必须同时清理两者；
 * 只删向量不删本表会导致关键词召回命中已删除文档。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-21
 */
@Data
@TableName("kb_chunk")
public class ChunkDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属知识库，检索时必须以此隔离，防止跨库越权召回 */
    private Long kbId;

    /** 所属文档 */
    private Long docId;

    /** 文档名冗余一份，关键词分支命中后无需再回查文档表 */
    private String docName;

    /** 页码估算值 */
    private Integer pageNo;

    /** 同一文档内从 1 开始的块序号 */
    private Integer chunkNo;

    /** 分块正文 */
    private String content;

    /** 0 未删除，1 已删除 */
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime gmtCreate;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime gmtModified;
}
