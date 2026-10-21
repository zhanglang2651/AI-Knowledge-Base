package com.zhanglang.kb.rag.dto;

import lombok.Data;

/**
 * 知识库可用文档数。
 * <p>
 * 用于列表页一次性统计，避免逐个知识库查询造成 N+1。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-21
 */
@Data
public class KbDocCount {

    /** 知识库 ID */
    private Long kbId;

    /** 该库下已完成解析且未删除的文档数 */
    private Integer count;
}
