package com.zhanglang.kb.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息展示对象。
 * <p>
 * {@code sources} 从消息表的 sourcesJson 反序列化而来，
 * 保证用户翻看历史时看到的引用与当时一致，不会因知识库变更而错乱。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
@Builder
public class MessageVO {

    private Long id;

    /** user / assistant */
    private String role;

    private String content;

    /** 引用列表，用户消息为空 */
    private List<SourceVO> sources;

    private Integer promptTokens;

    private Integer completionTokens;

    private LocalDateTime gmtCreate;
}
