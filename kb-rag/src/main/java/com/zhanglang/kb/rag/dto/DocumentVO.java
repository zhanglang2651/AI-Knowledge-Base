package com.zhanglang.kb.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档展示对象。
 * <p>
 * 列表页需要高频轮询 {@code parseStatus}，因此该字段必须实时反映数据库状态，
 * 不能做缓存，否则用户会看到"进度卡住不动"。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
@Builder
public class DocumentVO {

    private Long id;

    private Long kbId;

    private String fileName;

    /** 文件大小（字节），前端格式化为 MB 展示 */
    private Long fileSize;

    /** 后缀，不含点 */
    private String fileType;

    private Integer pageCount;

    /** 已切分块数，解析完成前为 0 */
    private Integer chunkCount;

    /** 解析状态：PENDING / PARSING / EMBEDDING / COMPLETED / FAILED */
    private String parseStatus;

    /** 失败原因，仅 FAILED 时有值 */
    private String errorMsg;

    private LocalDateTime gmtCreate;
}
