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
 * 文档实体。
 * <p>
 * 解析状态机：{@code PENDING → PARSING → EMBEDDING → COMPLETED}，
 * 任一步失败转 {@code FAILED} 并记录 errorMsg，支持有限次重试。
 * 前端通过轮询 parseStatus 展示进度，因此状态必须落库，不能只存在内存里。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
@TableName("kb_document")
public class DocumentDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属知识库 ID */
    private Long kbId;

    /** 原始文件名 */
    private String fileName;

    /** 存储对象键（本地存储为相对路径，MinIO 为 objectKey） */
    private String fileKey;

    /** 文件字节数 */
    private Long fileSize;

    /** 文件后缀（小写，不含点） */
    private String fileType;

    /** 文件 MD5，用于同库内去重 */
    private String fileMd5;

    /** 页数，PDF/Word 有效，纯文本为 0 */
    private Integer pageCount;

    /** 切分出的块数 */
    private Integer chunkCount;

    /** 解析状态：PENDING / PARSING / EMBEDDING / COMPLETED / FAILED */
    private String parseStatus;

    /** 已重试次数，超过上限不再自动重试 */
    private Integer retryCount;

    /** 失败原因，仅存简要描述，详细堆栈进日志 */
    private String errorMsg;

    /** 逻辑删除标记 */
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime gmtCreate;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime gmtModified;
}
