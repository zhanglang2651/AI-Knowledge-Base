package com.zhanglang.kb.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 问答请求。
 * <p>
 * {@code conversationId} 为空表示新建会话，由服务端创建后通过 SSE 的 done 事件回传，
 * 前端拿到后保存，后续提问复用该 ID。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
public class ChatRequest {

    /** 目标知识库 ID，必填 */
    @NotNull(message = "知识库 ID 不能为空")
    private Long kbId;

    /** 会话 ID，可空。为空则新建会话 */
    private Long conversationId;

    /**
     * 用户问题。
     * 长度上限 2000 字符：过长的输入会挤占检索结果在上下文中的空间，
     * 也会让 Token 成本失控，因此在入口处截断比在模型侧报错更友好。
     */
    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题长度不能超过 2000 字符")
    private String question;
}
