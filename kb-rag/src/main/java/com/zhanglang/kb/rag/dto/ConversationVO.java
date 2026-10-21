package com.zhanglang.kb.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表展示对象。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
@Builder
public class ConversationVO {

    private Long id;

    private Long kbId;

    /** 会话标题，取首条提问前 20 字 */
    private String title;

    /** 消息条数 */
    private Integer messageCount;

    /** 最后活跃时间，列表按此倒序 */
    private LocalDateTime lastMessageAt;
}
